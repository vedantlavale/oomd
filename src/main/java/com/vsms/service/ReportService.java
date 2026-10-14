package com.vsms.service;

import com.vsms.dto.DailyTotal;
import com.vsms.dto.MethodTotal;
import com.vsms.entity.Appointment;
import com.vsms.entity.Invoice;
import com.vsms.entity.Payment;
import com.vsms.entity.ServiceJob;
import com.vsms.enums.AppointmentStatus;
import com.vsms.enums.JobStatus;
import com.vsms.enums.PaymentMethod;
import com.vsms.enums.PaymentRecordStatus;
import com.vsms.enums.PaymentStatus;
import com.vsms.repository.AppointmentRepository;
import com.vsms.repository.InvoiceRepository;
import com.vsms.repository.PaymentRepository;
import com.vsms.repository.ServiceJobRepository;
import com.vsms.util.Filters;
import com.vsms.util.MoneyUtil;
import com.vsms.util.Texts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only reports. Each report returns the matching rows (up to {@link #ROW_LIMIT}) together with
 * totals, so the page can be printed as it is.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    /** Reports show at most this many rows; the page tells the user if more exist. */
    public static final int ROW_LIMIT = 500;

    private final AppointmentRepository appointments;
    private final ServiceJobRepository jobs;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;

    public ReportService(AppointmentRepository appointments, ServiceJobRepository jobs,
                         InvoiceRepository invoices, PaymentRepository payments) {
        this.appointments = appointments;
        this.jobs = jobs;
        this.invoices = invoices;
        this.payments = payments;
    }

    public record AppointmentReport(List<Appointment> rows, boolean truncated, long total,
                                    Map<AppointmentStatus, Long> byStatus) {
    }

    public record JobReport(List<ServiceJob> rows, boolean truncated, long total, Map<JobStatus, Long> byStatus) {
    }

    public record InvoiceReport(List<Invoice> rows, boolean truncated, long total,
                                BigDecimal invoiced, BigDecimal paid, BigDecimal outstanding) {
    }

    public record PaymentReport(List<Payment> rows, boolean truncated, long total, BigDecimal collected,
                                BigDecimal reversed) {
    }

    public record RevenueSummary(LocalDate from, LocalDate to,
                                 BigDecimal invoiced, long invoiceCount,
                                 BigDecimal collected, long paymentCount,
                                 BigDecimal outstandingNow, long unpaidInvoicesNow,
                                 List<MethodTotal> byMethod, List<DailyTotal> byDay) {
    }

    public AppointmentReport appointments(String query, AppointmentStatus status, Long serviceId,
                                          LocalDate from, LocalDate to) {
        Pageable limit = PageRequest.of(0, ROW_LIMIT, Sort.by(Sort.Order.asc("appointmentDate"), Sort.Order.asc("appointmentTime")));
        Page<Appointment> page = appointments.search(Texts.like(query), Texts.likeRegistration(query), status, from, to,
                serviceId, limit);
        Map<AppointmentStatus, Long> counts = new EnumMap<>(AppointmentStatus.class);
        for (Appointment appointment : page.getContent()) {
            counts.merge(appointment.getStatus(), 1L, Long::sum);
        }
        return new AppointmentReport(page.getContent(), page.hasNext(), page.getTotalElements(), counts);
    }

    public JobReport jobs(String query, JobStatus status, Long mechanicId, LocalDate from, LocalDate to) {
        Pageable limit = PageRequest.of(0, ROW_LIMIT, Sort.by(Sort.Order.desc("id")));
        Page<ServiceJob> page = jobs.search(Texts.like(query), Texts.likeRegistration(query), Texts.numberFromSearch(query),
                status, mechanicId, Filters.startOf(from), Filters.endOfInclusive(to), limit);
        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (ServiceJob job : page.getContent()) {
            counts.merge(job.getStatus(), 1L, Long::sum);
        }
        return new JobReport(page.getContent(), page.hasNext(), page.getTotalElements(), counts);
    }

    public InvoiceReport invoices(String query, PaymentStatus status, LocalDate from, LocalDate to) {
        Pageable limit = PageRequest.of(0, ROW_LIMIT, Sort.by(Sort.Order.desc("id")));
        Page<Invoice> page = invoices.search(Texts.like(query), Texts.likeRegistration(query), Texts.numberFromSearch(query),
                status, Filters.startOf(from), Filters.endOfInclusive(to), limit);
        BigDecimal invoiced = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (Invoice invoice : page.getContent()) {
            invoiced = invoiced.add(invoice.getGrandTotal());
            paid = paid.add(invoice.getAmountPaid());
        }
        return new InvoiceReport(page.getContent(), page.hasNext(), page.getTotalElements(),
                MoneyUtil.money(invoiced), MoneyUtil.money(paid), MoneyUtil.money(invoiced.subtract(paid)));
    }

    public PaymentReport payments(String query, PaymentMethod method, PaymentRecordStatus status,
                                  LocalDate from, LocalDate to) {
        Pageable limit = PageRequest.of(0, ROW_LIMIT, Sort.by(Sort.Order.desc("paidAt"), Sort.Order.desc("id")));
        Page<Payment> page = payments.search(Texts.like(query), Texts.likeRegistration(query), Texts.numberFromSearch(query),
                status, method, Filters.startOf(from), Filters.endOfInclusive(to), limit);
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal reversed = BigDecimal.ZERO;
        for (Payment payment : page.getContent()) {
            if (payment.getStatus() == PaymentRecordStatus.SUCCESSFUL) {
                collected = collected.add(payment.getAmount());
            } else {
                reversed = reversed.add(payment.getAmount());
            }
        }
        return new PaymentReport(page.getContent(), page.hasNext(), page.getTotalElements(),
                MoneyUtil.money(collected), MoneyUtil.money(reversed));
    }

    /** Revenue for the days from..to (both included): money invoiced, money actually collected, and how it was paid. */
    public RevenueSummary revenue(LocalDate from, LocalDate to) {
        var start = Filters.startOf(from);
        var end = Filters.endOfInclusive(to);
        List<MethodTotal> byMethod = payments.totalsByMethod(PaymentRecordStatus.SUCCESSFUL, start, end);
        List<DailyTotal> byDay = payments.totalsByDay(PaymentRecordStatus.SUCCESSFUL, start, end);
        long paymentCount = byMethod.stream().mapToLong(MethodTotal::count).sum();
        return new RevenueSummary(from, to,
                MoneyUtil.money(invoices.sumInvoicedBetween(start, end)), invoices.countIssuedBetween(start, end),
                MoneyUtil.money(payments.sumByStatusBetween(PaymentRecordStatus.SUCCESSFUL, start, end)), paymentCount,
                MoneyUtil.money(invoices.sumOutstanding(PaymentStatus.PAID)), invoices.countByPaymentStatusNot(PaymentStatus.PAID),
                byMethod, byDay.stream().sorted(Comparator.comparing(DailyTotal::date)).toList());
    }
}
