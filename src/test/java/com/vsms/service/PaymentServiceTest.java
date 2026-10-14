package com.vsms.service;

import static com.vsms.support.Rules.assertRejected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vsms.AbstractIntegrationTest;
import com.vsms.entity.Invoice;
import com.vsms.entity.Payment;
import com.vsms.entity.ServiceJob;
import com.vsms.enums.JobStatus;
import com.vsms.enums.PaymentMethod;
import com.vsms.enums.PaymentRecordStatus;
import com.vsms.enums.PaymentStatus;
import com.vsms.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PaymentServiceTest extends AbstractIntegrationTest {

    @Autowired
    PaymentService paymentService;
    @Autowired
    InvoiceService invoiceService;
    @Autowired
    ServiceJobService jobService;
    @Autowired
    ReportService reportService;
    @Autowired
    TestData data;

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /** An invoice for exactly 1000.00 (no tax). */
    private Invoice invoice() {
        ServiceJob job = data.completedJob("1000.00");
        return invoiceService.generate(job.getId(), BigDecimal.ZERO, data.staff());
    }

    private Payment pay(Invoice invoice, String amount, PaymentMethod method) {
        return paymentService.record(data.paymentForm(invoice.getId(), amount, method), data.staff());
    }

    @Test
    void aPartialPaymentLeavesTheInvoicePartiallyPaid() {
        Invoice invoice = invoice();
        pay(invoice, "400.00", PaymentMethod.CASH);
        assertEquals(PaymentStatus.PARTIALLY_PAID, invoice.getPaymentStatus());
        assertEquals(d("400.00"), invoice.getAmountPaid());
        assertEquals(d("600.00"), invoice.getBalanceDue());
    }

    @Test
    void payingTheBalanceMakesTheInvoicePaid() {
        Invoice invoice = invoice();
        pay(invoice, "400.00", PaymentMethod.CASH);
        pay(invoice, "600.00", PaymentMethod.UPI);
        assertEquals(PaymentStatus.PAID, invoice.getPaymentStatus());
        assertEquals(d("1000.00"), invoice.getAmountPaid());
        assertEquals(d("0.00"), invoice.getBalanceDue());
    }

    @Test
    void aSinglePaymentCanSettleTheWholeInvoice() {
        Invoice invoice = invoice();
        pay(invoice, "1000.00", PaymentMethod.CARD);
        assertEquals(PaymentStatus.PAID, invoice.getPaymentStatus());
    }

    @Test
    void paymentsAboveTheOutstandingBalanceAreRejected() {
        Invoice invoice = invoice();
        pay(invoice, "700.00", PaymentMethod.CASH);
        assertRejected("amount", "more than the outstanding balance", () -> pay(invoice, "300.01", PaymentMethod.CASH));
        assertEquals(d("700.00"), invoice.getAmountPaid());
        assertEquals(PaymentStatus.PARTIALLY_PAID, invoice.getPaymentStatus());
    }

    @Test
    void zeroNegativeAndTooPreciseAmountsAreRejected() {
        Invoice invoice = invoice();
        assertRejected("amount", "greater than zero", () -> pay(invoice, "0", PaymentMethod.CASH));
        assertRejected("amount", "greater than zero", () -> pay(invoice, "-50.00", PaymentMethod.CASH));
        assertRejected("amount", "2 decimal places", () -> pay(invoice, "10.005", PaymentMethod.CASH));
        assertEquals(PaymentStatus.UNPAID, invoice.getPaymentStatus());
        assertEquals(d("0.00"), invoice.getAmountPaid());
    }

    @Test
    void aFullyPaidInvoiceAcceptsNoMorePayments() {
        Invoice invoice = invoice();
        pay(invoice, "1000.00", PaymentMethod.OTHER);
        assertRejected("already fully paid", () -> pay(invoice, "1.00", PaymentMethod.CASH));
    }

    @Test
    void mechanicsCannotRecordPayments() {
        Invoice invoice = invoice();
        assertThrows(AccessDeniedException.class, () -> paymentService.record(
                data.paymentForm(invoice.getId(), "10.00", PaymentMethod.CASH), data.mechanicActor(data.mechanic())));
    }

    @Test
    void theVehicleCanOnlyBeDeliveredOnceTheInvoiceIsFullyPaid() {
        Invoice invoice = invoice();
        ServiceJob job = invoice.getServiceJob();
        assertRejected("fully paid", () -> jobService.changeStatus(job.getId(), JobStatus.DELIVERED, data.staff()));
        pay(invoice, "999.99", PaymentMethod.CASH);
        assertRejected("fully paid", () -> jobService.changeStatus(job.getId(), JobStatus.DELIVERED, data.staff()));
        pay(invoice, "0.01", PaymentMethod.CASH);
        jobService.changeStatus(job.getId(), JobStatus.DELIVERED, data.staff());
        assertEquals(JobStatus.DELIVERED, job.getStatus());
    }

    @Test
    void reversingAPaymentReducesThePaidAmountAndKeepsTheRecord() {
        Invoice invoice = invoice();
        Payment first = pay(invoice, "1000.00", PaymentMethod.UPI);
        assertEquals(PaymentStatus.PAID, invoice.getPaymentStatus());

        paymentService.reverse(first.getId(), "Entered twice", data.admin());
        assertEquals(PaymentRecordStatus.REVERSED, first.getStatus());
        assertEquals(PaymentStatus.UNPAID, invoice.getPaymentStatus());
        assertEquals(d("0.00"), invoice.getAmountPaid());
        assertRejected("already been reversed", () -> paymentService.reverse(first.getId(), "again", data.admin()));
        // the full amount can be paid again
        pay(invoice, "1000.00", PaymentMethod.CASH);
        assertEquals(PaymentStatus.PAID, invoice.getPaymentStatus());
    }

    @Test
    void onlyAdministratorsCanReverseAndAReasonIsRequired() {
        Invoice invoice = invoice();
        Payment payment = pay(invoice, "100.00", PaymentMethod.CASH);
        assertThrows(AccessDeniedException.class, () -> paymentService.reverse(payment.getId(), "x", data.staff()));
        assertRejected("reason", () -> paymentService.reverse(payment.getId(), "  ", data.admin()));
    }

    @Test
    void revenueCountsOnlySuccessfulPayments() {
        LocalDate today = LocalDate.now();
        BigDecimal before = reportService.revenue(today, today).collected();

        Invoice invoice = invoice();
        pay(invoice, "300.00", PaymentMethod.CASH);
        Payment mistaken = pay(invoice, "200.00", PaymentMethod.UPI);
        assertEquals(before.add(d("500.00")), reportService.revenue(today, today).collected());

        paymentService.reverse(mistaken.getId(), "Wrong invoice", data.admin());
        var summary = reportService.revenue(today, today);
        assertEquals(before.add(d("300.00")), summary.collected());
        assertTrue(summary.byMethod().stream().anyMatch(m -> m.method() == PaymentMethod.CASH));
        assertTrue(summary.byDay().stream().anyMatch(day -> day.date().equals(today)));
    }
}
