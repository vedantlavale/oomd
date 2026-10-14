package com.vsms.service;

import com.vsms.entity.Appointment;
import com.vsms.entity.ServiceJob;
import com.vsms.enums.JobStatus;
import com.vsms.enums.PaymentRecordStatus;
import com.vsms.enums.PaymentStatus;
import com.vsms.repository.AppointmentRepository;
import com.vsms.repository.CustomerRepository;
import com.vsms.repository.InvoiceRepository;
import com.vsms.repository.PaymentRepository;
import com.vsms.repository.ServiceJobRepository;
import com.vsms.repository.SparePartRepository;
import com.vsms.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final AppointmentService appointments;
    private final ServiceJobRepository jobs;
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final SparePartRepository parts;
    private final Clock clock;

    public DashboardService(CustomerRepository customers, VehicleRepository vehicles, AppointmentService appointments,
                            ServiceJobRepository jobs, PaymentRepository payments, InvoiceRepository invoices,
                            SparePartRepository parts, Clock clock) {
        this.customers = customers;
        this.vehicles = vehicles;
        this.appointments = appointments;
        this.jobs = jobs;
        this.payments = payments;
        this.invoices = invoices;
        this.parts = parts;
        this.clock = clock;
    }

    /** Numbers and lists for administrators and service advisors. */
    public record StaffDashboard(
            long customerCount, long vehicleCount,
            long todayAppointmentCount, List<Appointment> todayAppointments,
            long pendingJobs, long inProgressJobs, long onHoldJobs, long completedJobs,
            BigDecimal revenueTotal, BigDecimal revenueThisMonth, BigDecimal revenueToday,
            BigDecimal outstanding, long unpaidInvoices, long lowStockParts,
            List<ServiceJob> recentJobs) {
    }

    /** What a mechanic sees: only their own work. */
    public record MechanicDashboard(boolean linked, long assigned, long inProgress, long onHold, List<ServiceJob> openJobs) {
    }

    public StaffDashboard staff() {
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal total = payments.sumByStatus(PaymentRecordStatus.SUCCESSFUL);
        BigDecimal month = payments.sumByStatusBetween(PaymentRecordStatus.SUCCESSFUL,
                monthStart.atStartOfDay(), today.plusDays(1).atStartOfDay());
        BigDecimal day = payments.sumByStatusBetween(PaymentRecordStatus.SUCCESSFUL,
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        return new StaffDashboard(
                customers.count(), vehicles.count(),
                appointments.countToday(), appointments.today(),
                jobs.countByStatusIn(List.of(JobStatus.CREATED, JobStatus.ASSIGNED)),
                jobs.countByStatusIn(List.of(JobStatus.IN_PROGRESS)),
                jobs.countByStatusIn(List.of(JobStatus.ON_HOLD)),
                jobs.countByStatusIn(JobStatus.FINISHED),
                total, month, day,
                invoices.sumOutstanding(PaymentStatus.PAID), invoices.countByPaymentStatusNot(PaymentStatus.PAID),
                parts.countLowStock(),
                jobs.findTop8ByOrderByIdDesc());
    }

    public MechanicDashboard mechanic(Long mechanicId) {
        if (mechanicId == null) {
            return new MechanicDashboard(false, 0, 0, 0, List.of());
        }
        return new MechanicDashboard(true,
                jobs.countByMechanicIdAndStatusIn(mechanicId, List.of(JobStatus.ASSIGNED)),
                jobs.countByMechanicIdAndStatusIn(mechanicId, List.of(JobStatus.IN_PROGRESS)),
                jobs.countByMechanicIdAndStatusIn(mechanicId, List.of(JobStatus.ON_HOLD)),
                jobs.findTop8ByMechanicIdAndStatusInOrderByIdDesc(mechanicId, JobStatus.MECHANIC_OPEN));
    }
}
