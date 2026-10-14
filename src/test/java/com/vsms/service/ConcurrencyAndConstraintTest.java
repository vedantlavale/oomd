package com.vsms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vsms.AbstractIntegrationTest;
import com.vsms.entity.Customer;
import com.vsms.entity.Invoice;
import com.vsms.entity.Mechanic;
import com.vsms.entity.ServiceCatalogItem;
import com.vsms.entity.ServiceJob;
import com.vsms.entity.SparePart;
import com.vsms.entity.Vehicle;
import com.vsms.enums.PaymentMethod;
import com.vsms.exception.BusinessRuleException;
import com.vsms.repository.InvoiceRepository;
import com.vsms.repository.SparePartRepository;
import com.vsms.repository.VehicleRepository;
import com.vsms.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * These tests are deliberately NOT transactional: every service call commits for real, from several
 * threads at once, so the row locks and the database's own constraints are what is being tested.
 * Whatever they create is removed afterwards.
 */
class ConcurrencyAndConstraintTest extends AbstractIntegrationTest {

    @Autowired
    TestData data;
    @Autowired
    ServiceJobService jobService;
    @Autowired
    InvoiceService invoiceService;
    @Autowired
    PaymentService paymentService;
    @Autowired
    AppointmentService appointmentService;
    @Autowired
    SparePartRepository parts;
    @Autowired
    VehicleRepository vehicles;
    @Autowired
    InvoiceRepository invoices;
    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void removeWhatTheTestCreated() {
        String vehicleIds = "(select id from vehicles where registration_number like 'TS%' or registration_number like 'DBX%')";
        String appointmentIds = "(select id from appointments where vehicle_id in " + vehicleIds + ")";
        String jobIds = "(select id from service_jobs where appointment_id in " + appointmentIds + ")";
        jdbc.update("delete from payments where invoice_id in (select id from invoices where service_job_id in " + jobIds + ")");
        jdbc.update("delete from invoices where service_job_id in " + jobIds);
        jdbc.update("delete from part_usages where service_job_id in " + jobIds);
        jdbc.update("delete from service_tasks where service_job_id in " + jobIds);
        jdbc.update("delete from service_jobs where appointment_id in " + appointmentIds);
        jdbc.update("delete from appointments where vehicle_id in " + vehicleIds);
        jdbc.update("delete from vehicles where registration_number like 'TS%' or registration_number like 'DBX%'");
        jdbc.update("delete from customers where name like 'Test Customer%'");
        jdbc.update("delete from service_catalog_items where name like 'Test service%'");
        jdbc.update("delete from mechanics where name like 'Test Mechanic%'");
        jdbc.update("delete from spare_parts where sku like 'TS-%'");
    }

    /** Runs the tasks at the same moment on separate threads and returns how many succeeded / were refused. */
    private record Outcome(int succeeded, int refused, List<Throwable> unexpected) {
    }

    private Outcome race(List<Callable<?>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<?> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get();
                ok.incrementAndGet();
            } catch (java.util.concurrent.ExecutionException ex) {
                if (ex.getCause() instanceof BusinessRuleException) {
                    refused.incrementAndGet();
                } else {
                    unexpected.add(ex.getCause());
                }
            }
        }
        pool.shutdown();
        return new Outcome(ok.get(), refused.get(), unexpected);
    }

    @Test
    void sixPeopleGrabbingTheLastFivePartsGetExactlyFive() throws Exception {
        SparePart part = data.part(5, "10.00");
        List<ServiceJob> targetJobs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            targetJobs.add(data.assignedJob(data.mechanic(), "100.00"));
        }
        List<Callable<?>> tasks = targetJobs.stream()
                .<Callable<?>>map(job -> () -> jobService.addPart(job.getId(), part.getId(), 1, data.staff()))
                .toList();

        Outcome outcome = race(tasks);

        assertTrue(outcome.unexpected().isEmpty(), "unexpected errors: " + outcome.unexpected());
        assertEquals(5, outcome.succeeded());
        assertEquals(1, outcome.refused());
        assertEquals(0, parts.findById(part.getId()).orElseThrow().getStockQuantity(), "stock must be exactly zero, never negative");
    }

    @Test
    void onlyOneOfSeveralSimultaneousInvoiceRequestsSucceeds() throws Exception {
        ServiceJob job = data.completedJob("500.00");
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> invoiceService.generate(job.getId(), new BigDecimal("18"), data.staff()));
        }

        Outcome outcome = race(tasks);

        assertTrue(outcome.unexpected().isEmpty(), "unexpected errors: " + outcome.unexpected());
        assertEquals(1, outcome.succeeded());
        assertEquals(4, outcome.refused());
        assertEquals(1, invoices.findAll().stream().filter(i -> i.getServiceJob().getId().equals(job.getId())).count());
    }

    @Test
    void simultaneousPaymentsCannotExceedTheInvoiceBalance() throws Exception {
        ServiceJob job = data.completedJob("1000.00");
        Invoice invoice = invoiceService.generate(job.getId(), BigDecimal.ZERO, data.staff());
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> paymentService.record(data.paymentForm(invoice.getId(), "600.00", PaymentMethod.CASH), data.staff()));
        }

        Outcome outcome = race(tasks);

        assertTrue(outcome.unexpected().isEmpty(), "unexpected errors: " + outcome.unexpected());
        assertEquals(1, outcome.succeeded(), "only one 600.00 payment fits into a 1000.00 invoice");
        assertEquals(new BigDecimal("600.00"), invoiceService.get(invoice.getId()).getAmountPaid());
    }

    @Test
    void simultaneousBookingsOfTheSameSlotLetExactlyOneThrough() throws Exception {
        ServiceCatalogItem service = data.service("1000.00");
        LocalDate day = LocalDate.now().plusDays(4);
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Customer customer = data.customer();
            Vehicle vehicle = data.vehicle(customer);
            tasks.add(() -> appointmentService.book(data.appointmentForm(customer, vehicle, service, day, LocalTime.of(10, 30))));
        }

        Outcome outcome = race(tasks);

        assertTrue(outcome.unexpected().isEmpty(), "unexpected errors: " + outcome.unexpected());
        assertEquals(1, outcome.succeeded());
        assertEquals(4, outcome.refused());
    }

    // ---- the database refuses bad data even if the application code were bypassed

    @Test
    void theDatabaseRejectsDuplicateRegistrationNumbers() {
        Customer owner = data.customer();
        Vehicle first = data.vehicle(owner);
        Vehicle clash = new Vehicle();
        clash.setCustomer(owner);
        clash.setRegistrationNumber(first.getRegistrationNumber());
        clash.setManufacturer("X");
        clash.setModel("Y");
        clash.setManufacturingYear(2020);
        clash.setFuelType(first.getFuelType());
        assertThrows(DataIntegrityViolationException.class, () -> vehicles.saveAndFlush(clash));
    }

    @Test
    void theDatabaseAllowsOnlyOneInvoicePerJob() {
        ServiceJob job = data.completedJob("100.00");
        Invoice real = invoiceService.generate(job.getId(), BigDecimal.ZERO, data.staff());
        Invoice second = new Invoice();
        second.setServiceJob(jobService.get(job.getId(), data.staff()));
        second.setLaborCharge(real.getLaborCharge());
        second.setPartsTotal(real.getPartsTotal());
        second.setSubtotal(real.getSubtotal());
        second.setTaxPercent(real.getTaxPercent());
        second.setTaxAmount(real.getTaxAmount());
        second.setGrandTotal(real.getGrandTotal());
        second.setIssuedAt(real.getIssuedAt());
        assertThrows(DataIntegrityViolationException.class, () -> invoices.saveAndFlush(second));
    }

    @Test
    void theDatabaseRefusesNegativeStockAndOverpayment() {
        // A CHECK violation is reported as DataIntegrityViolationException by H2 but as a generic JPA error by
        // MySQL, so the common parent type is asserted.
        SparePart part = data.part(3, "10.00");
        part.setStockQuantity(-1);
        assertThrows(DataAccessException.class, () -> parts.saveAndFlush(part));

        ServiceJob job = data.completedJob("100.00");
        Invoice invoice = invoiceService.generate(job.getId(), BigDecimal.ZERO, data.staff());
        invoice.setAmountPaid(new BigDecimal("100.01"));
        assertThrows(DataAccessException.class, () -> invoices.saveAndFlush(invoice));
    }

    @Test
    void aMechanicCanHaveOnlyOneLoginAccount() {
        Mechanic mechanic = data.mechanic();
        jdbc.update("insert into user_accounts (created_at, enabled, full_name, password_hash, role, username, mechanic_id) "
                + "values (now(), true, 'A', 'x', 'MECHANIC', 'dup.one', ?)", mechanic.getId());
        try {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "insert into user_accounts (created_at, enabled, full_name, password_hash, role, username, mechanic_id) "
                            + "values (now(), true, 'B', 'x', 'MECHANIC', 'dup.two', ?)", mechanic.getId()));
        } finally {
            jdbc.update("delete from user_accounts where username in ('dup.one', 'dup.two')");
        }
    }
}
