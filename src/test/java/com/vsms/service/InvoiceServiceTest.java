package com.vsms.service;

import static com.vsms.support.Rules.assertRejected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vsms.AbstractIntegrationTest;
import com.vsms.entity.Invoice;
import com.vsms.entity.ServiceJob;
import com.vsms.entity.SparePart;
import com.vsms.enums.JobStatus;
import com.vsms.enums.PaymentStatus;
import com.vsms.repository.SparePartRepository;
import com.vsms.support.TestData;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class InvoiceServiceTest extends AbstractIntegrationTest {

    @Autowired
    InvoiceService invoiceService;
    @Autowired
    ServiceJobService jobService;
    @Autowired
    SparePartRepository partRepository;
    @Autowired
    TestData data;

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void totalsAreCalculatedFromLabourPartsAndTax() {
        ServiceJob job = data.completedJob("1000.00");
        SparePart oil = data.part(20, "250.00");
        SparePart filter = data.part(20, "99.99");
        jobService.addPart(job.getId(), oil.getId(), 2, data.staff());     // 500.00
        jobService.addPart(job.getId(), filter.getId(), 3, data.staff());  // 299.97

        Invoice invoice = invoiceService.generate(job.getId(), d("18"), data.staff());

        assertEquals(d("1000.00"), invoice.getLaborCharge());
        assertEquals(d("799.97"), invoice.getPartsTotal());
        assertEquals(d("1799.97"), invoice.getSubtotal());
        assertEquals(d("18.00"), invoice.getTaxPercent());
        assertEquals(d("323.99"), invoice.getTaxAmount());   // 1799.97 x 18% = 323.9946, rounded half-up
        assertEquals(d("2123.96"), invoice.getGrandTotal());
        assertEquals(d("0.00"), invoice.getAmountPaid());
        assertEquals(PaymentStatus.UNPAID, invoice.getPaymentStatus());
        assertEquals(JobStatus.INVOICED, job.getStatus());
    }

    @Test
    void taxIsOptional() {
        ServiceJob job = data.completedJob("500.00");
        Invoice invoice = invoiceService.generate(job.getId(), BigDecimal.ZERO, data.staff());
        assertEquals(d("0.00"), invoice.getTaxAmount());
        assertEquals(d("500.00"), invoice.getGrandTotal());
    }

    @Test
    void aJobCanOnlyBeInvoicedOnce() {
        ServiceJob job = data.completedJob("500.00");
        Invoice first = invoiceService.generate(job.getId(), d("18"), data.staff());
        assertNotNull(first.getId());
        assertRejected("already been generated", () -> invoiceService.generate(job.getId(), d("18"), data.staff()));
        assertEquals(first.getId(), invoiceService.findByJob(job.getId()).getId());
    }

    @Test
    void onlyCompletedJobsCanBeInvoiced() {
        ServiceJob created = data.job("500.00");
        assertRejected("completed job", () -> invoiceService.generate(created.getId(), d("18"), data.staff()));
        ServiceJob inProgress = data.assignedJob(data.mechanic(), "500.00");
        jobService.changeStatus(inProgress.getId(), JobStatus.IN_PROGRESS, data.staff());
        assertRejected("completed job", () -> invoiceService.generate(inProgress.getId(), d("18"), data.staff()));
        assertNull(invoiceService.findByJob(created.getId()));
    }

    @Test
    void invalidTaxRatesAreRejected() {
        ServiceJob job = data.completedJob("500.00");
        assertRejected("taxPercent", "between 0 and 100", () -> invoiceService.generate(job.getId(), d("101"), data.staff()));
        assertRejected("taxPercent", "between 0 and 100", () -> invoiceService.generate(job.getId(), d("-1"), data.staff()));
        assertEquals(JobStatus.COMPLETED, job.getStatus());
    }

    @Test
    void mechanicsCannotGenerateInvoices() {
        ServiceJob job = data.completedJob("500.00");
        assertThrows(AccessDeniedException.class,
                () -> invoiceService.generate(job.getId(), d("18"), data.mechanicActor(data.mechanic())));
    }

    @Test
    void invoiceAmountsAreFrozenWhenCataloguePricesChangeLater() {
        ServiceJob job = data.completedJob("1000.00");
        SparePart part = data.part(10, "200.00");
        jobService.addPart(job.getId(), part.getId(), 2, data.staff());
        Invoice invoice = invoiceService.generate(job.getId(), d("10"), data.staff());
        assertEquals(d("1540.00"), invoice.getGrandTotal());   // (1000 + 400) x 1.10

        part.setUnitPrice(d("5000.00"));
        partRepository.saveAndFlush(part);
        job.setLaborCharge(d("9999.00")); // even a changed job record must not alter a stored invoice

        Invoice reloaded = invoiceService.get(invoice.getId());
        assertEquals(d("400.00"), reloaded.getPartsTotal());
        assertEquals(d("1000.00"), reloaded.getLaborCharge());
        assertEquals(d("1540.00"), reloaded.getGrandTotal());
        assertEquals(d("400.00"), job.getPartUsages().get(0).getLineTotal());
    }

    @Test
    void previewShowsTheAmountsAtTheDefaultRateWithoutSavingAnything() {
        ServiceJob job = data.completedJob("1000.00");
        var preview = invoiceService.preview(job.getId());
        assertEquals(d("1000.00"), preview.totals().subtotal());
        assertEquals(d("18.00"), preview.defaultTaxPercent());
        assertNull(invoiceService.findByJob(job.getId()));
        assertEquals(JobStatus.COMPLETED, job.getStatus());
    }
}
