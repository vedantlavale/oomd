package com.vsms.service;

import com.vsms.dto.PaymentForm;
import com.vsms.entity.Invoice;
import com.vsms.entity.Payment;
import com.vsms.enums.PaymentMethod;
import com.vsms.enums.PaymentRecordStatus;
import com.vsms.enums.PaymentStatus;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.repository.InvoiceRepository;
import com.vsms.repository.PaymentRepository;
import com.vsms.security.Actor;
import com.vsms.util.Filters;
import com.vsms.util.MoneyUtil;
import com.vsms.util.Paging;
import com.vsms.util.Texts;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records payments against invoices. There is no payment gateway: a payment record means the money
 * has already been received (cash, UPI, card, other).
 */
@Service
@Transactional
public class PaymentService {

    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, InvoiceRepository invoices, Clock clock) {
        this.payments = payments;
        this.invoices = invoices;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Payment> search(String query, PaymentRecordStatus status, PaymentMethod method,
                                LocalDate from, LocalDate to, int page) {
        return payments.search(Texts.like(query), Texts.likeRegistration(query), Texts.numberFromSearch(query),
                status, method, Filters.startOf(from), Filters.endOfInclusive(to),
                Paging.of(page, Sort.by(Sort.Order.desc("paidAt"), Sort.Order.desc("id"))));
    }

    @Transactional(readOnly = true)
    public Payment get(Long id) {
        return payments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }

    /**
     * Adds a payment. The invoice row is locked first so two simultaneous payments cannot together
     * exceed the balance; then the invoice's paid amount and status are recalculated.
     */
    public Payment record(PaymentForm form, Actor actor) {
        if (actor == null || !actor.isStaff()) {
            throw new AccessDeniedException("Only administrators and service advisors can record payments.");
        }
        Invoice invoice = invoices.findByIdForUpdate(form.getInvoiceId())
                .orElseThrow(() -> new BusinessRuleException("invoiceId", "Choose an existing invoice."));

        BigDecimal amount = form.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("amount", "The amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > MoneyUtil.SCALE) {
            throw new BusinessRuleException("amount", "Use at most 2 decimal places.");
        }
        amount = MoneyUtil.money(amount);

        BigDecimal alreadyPaid = payments.sumByInvoiceAndStatus(invoice.getId(), PaymentRecordStatus.SUCCESSFUL);
        BigDecimal outstanding = invoice.getGrandTotal().subtract(alreadyPaid);
        if (outstanding.signum() <= 0) {
            throw new BusinessRuleException("amount", "This invoice is already fully paid.");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessRuleException("amount", "The amount is more than the outstanding balance of "
                    + outstanding.toPlainString() + ".");
        }

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setMethod(form.getMethod());
        payment.setReference(Texts.clean(form.getReference()));
        payment.setStatus(PaymentRecordStatus.SUCCESSFUL);
        payment.setPaidAt(LocalDateTime.now(clock));
        payment.setRecordedBy(actor.username());
        payments.saveAndFlush(payment);

        refreshInvoice(invoice);
        return payment;
    }

    /** Administrator only: marks a wrongly entered payment as reversed (it stops counting as revenue). */
    public Payment reverse(Long paymentId, String reason, Actor actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new AccessDeniedException("Only administrators can reverse payments.");
        }
        Payment payment = get(paymentId);
        Invoice invoice = invoices.findByIdForUpdate(payment.getInvoice().getId()).orElseThrow();
        if (payment.getStatus() == PaymentRecordStatus.REVERSED) {
            throw new BusinessRuleException("This payment has already been reversed.");
        }
        String cleaned = Texts.clean(reason);
        if (cleaned == null) {
            throw new BusinessRuleException("Enter a reason for reversing the payment.");
        }
        if (cleaned.length() > 200) {
            throw new BusinessRuleException("The reason can have at most 200 characters.");
        }
        payment.setStatus(PaymentRecordStatus.REVERSED);
        payment.setReversedAt(LocalDateTime.now(clock));
        payment.setReversalReason(cleaned);
        payments.saveAndFlush(payment);

        refreshInvoice(invoice);
        return payment;
    }

    /** Recomputes the paid amount from the successful payments and sets the invoice's payment status. */
    private void refreshInvoice(Invoice invoice) {
        BigDecimal paid = MoneyUtil.money(payments.sumByInvoiceAndStatus(invoice.getId(), PaymentRecordStatus.SUCCESSFUL));
        invoice.setAmountPaid(paid);
        if (paid.compareTo(invoice.getGrandTotal()) >= 0) {
            invoice.setPaymentStatus(PaymentStatus.PAID);
        } else if (paid.signum() > 0) {
            invoice.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
        } else {
            invoice.setPaymentStatus(PaymentStatus.UNPAID);
        }
    }
}
