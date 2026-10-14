package com.vsms.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vsms.AbstractIntegrationTest;
import com.vsms.entity.Invoice;
import com.vsms.enums.JobStatus;
import com.vsms.enums.PaymentStatus;
import com.vsms.enums.Role;
import com.vsms.repository.CustomerRepository;
import com.vsms.repository.InvoiceRepository;
import com.vsms.repository.ServiceJobRepository;
import com.vsms.repository.SparePartRepository;
import com.vsms.repository.UserAccountRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** The fictional demo data loaded at start-up must be complete and consistent. */
@Transactional
class SampleDataTest extends AbstractIntegrationTest {

    @Autowired
    CustomerRepository customers;
    @Autowired
    ServiceJobRepository jobs;
    @Autowired
    InvoiceRepository invoices;
    @Autowired
    SparePartRepository parts;
    @Autowired
    UserAccountRepository users;

    @Test
    void sampleDataCoversEveryJobStatus() {
        assertTrue(customers.count() >= 8);
        for (JobStatus status : new JobStatus[]{JobStatus.CREATED, JobStatus.ASSIGNED, JobStatus.IN_PROGRESS,
                JobStatus.ON_HOLD, JobStatus.COMPLETED, JobStatus.INVOICED, JobStatus.DELIVERED}) {
            assertTrue(jobs.countByStatusIn(java.util.List.of(status)) >= 1, "no sample job with status " + status);
        }
    }

    @Test
    void sampleInvoicesHaveTheExpectedTotalsAndPaymentStates() {
        var all = invoices.findAll();
        Invoice paid = all.stream().filter(i -> i.getPaymentStatus() == PaymentStatus.PAID).findFirst().orElseThrow();
        // 3999.00 labour + 4 x 520.00 oil + 180.00 filter + 320.00 air filter = 6579.00; + 18% = 7763.22
        assertEquals(new BigDecimal("7763.22"), paid.getGrandTotal());
        assertEquals(paid.getGrandTotal(), paid.getAmountPaid());
        assertTrue(all.stream().anyMatch(i -> i.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID));
        assertTrue(all.stream().anyMatch(i -> i.getPaymentStatus() == PaymentStatus.UNPAID));
        for (Invoice invoice : all) {
            assertEquals(invoice.getSubtotal().add(invoice.getTaxAmount()), invoice.getGrandTotal());
            assertTrue(invoice.getAmountPaid().compareTo(invoice.getGrandTotal()) <= 0);
        }
    }

    @Test
    void sampleStockWasDeductedAndAtLeastOnePartIsLow() {
        assertTrue(parts.countLowStock() >= 1);
        // engine oil: 60 in stock at the start, 4 used on the delivered job and 3 on the job in progress
        assertEquals(53, parts.findBySku("OIL-5W30-1L").orElseThrow().getStockQuantity());
    }

    @Test
    void sampleAccountsExistForEveryRole() {
        assertTrue(users.existsByRole(Role.ADMIN));
        assertTrue(users.existsByRole(Role.SERVICE_ADVISOR));
        assertTrue(users.existsByRole(Role.MECHANIC));
        assertTrue(users.findByUsername("ravi.k").orElseThrow().getMechanic() != null);
    }
}
