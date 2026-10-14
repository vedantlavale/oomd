package com.vsms.service;

import com.vsms.config.VsmsProperties;
import com.vsms.entity.Invoice;
import com.vsms.entity.ServiceJob;
import com.vsms.enums.JobStatus;
import com.vsms.enums.PaymentStatus;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.repository.InvoiceRepository;
import com.vsms.repository.ServiceJobRepository;
import com.vsms.security.Actor;
import com.vsms.util.Filters;
import com.vsms.util.InvoiceCalculator.Totals;
import com.vsms.util.InvoiceCalculator;
import com.vsms.util.Paging;
import com.vsms.util.Texts;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final ServiceJobRepository jobs;
    private final VsmsProperties properties;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, ServiceJobRepository jobs, VsmsProperties properties, Clock clock) {
        this.invoices = invoices;
        this.jobs = jobs;
        this.properties = properties;
        this.clock = clock;
    }

    /** What an invoice for a job would contain (nothing is saved). */
    public record InvoicePreview(ServiceJob job, Totals totals, BigDecimal defaultTaxPercent) {
    }

    @Transactional(readOnly = true)
    public Page<Invoice> search(String query, PaymentStatus status, LocalDate from, LocalDate to, int page) {
        return invoices.search(Texts.like(query), Texts.likeRegistration(query), Texts.numberFromSearch(query),
                status, Filters.startOf(from), Filters.endOfInclusive(to),
                Paging.of(page, Sort.by(Sort.Order.desc("id"))));
    }

    @Transactional(readOnly = true)
    public Invoice get(Long id) {
        return invoices.findById(id).orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    @Transactional(readOnly = true)
    public Invoice findByJob(Long jobId) {
        return invoices.findByServiceJobId(jobId).orElse(null);
    }

    @Transactional(readOnly = true)
    public InvoicePreview preview(Long jobId) {
        ServiceJob job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Job card", jobId));
        BigDecimal defaultTax = properties.billing().defaultTaxPercent();
        return new InvoicePreview(job, InvoiceCalculator.calculate(job.getLaborCharge(), job.getPartsTotal(), defaultTax),
                defaultTax);
    }

    /**
     * Generates the invoice for a completed job. The totals are calculated here on the server from
     * the labour charge and the part lines (with the prices charged when they were used).
     */
    public Invoice generate(Long jobId, BigDecimal taxPercent, Actor actor) {
        if (actor == null || !actor.isStaff()) {
            throw new AccessDeniedException("Only administrators and service advisors can generate invoices.");
        }
        // Locking the job row makes a second, simultaneous request wait and then see the invoice.
        ServiceJob job = jobs.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job card", jobId));
        invoices.findByServiceJobId(jobId).ifPresent(existing -> {
            throw new BusinessRuleException("Invoice " + existing.getInvoiceNumber() + " has already been generated for this job.");
        });
        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new BusinessRuleException("An invoice can only be generated for a completed job. This job is "
                    + job.getStatus().getLabel().toLowerCase() + ".");
        }
        Totals totals = InvoiceCalculator.calculate(job.getLaborCharge(), job.getPartsTotal(), taxPercent);

        Invoice invoice = new Invoice();
        invoice.setServiceJob(job);
        invoice.setLaborCharge(totals.laborCharge());
        invoice.setPartsTotal(totals.partsTotal());
        invoice.setSubtotal(totals.subtotal());
        invoice.setTaxPercent(totals.taxPercent());
        invoice.setTaxAmount(totals.taxAmount());
        invoice.setGrandTotal(totals.grandTotal());
        invoice.setAmountPaid(BigDecimal.ZERO.setScale(2));
        invoice.setPaymentStatus(totals.grandTotal().signum() == 0 ? PaymentStatus.PAID : PaymentStatus.UNPAID);
        invoice.setIssuedAt(LocalDateTime.now(clock));
        invoice.setIssuedBy(actor.username());
        try {
            invoices.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("An invoice has already been generated for this job.");
        }
        job.setStatus(JobStatus.INVOICED);
        return invoice;
    }
}
