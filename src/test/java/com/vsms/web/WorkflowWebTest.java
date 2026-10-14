package com.vsms.web;

import static com.vsms.web.WebTestSupport.idFromRedirect;
import static com.vsms.web.WebTestSupport.login;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsms.AbstractIntegrationTest;
import com.vsms.entity.Invoice;
import com.vsms.entity.ServiceJob;
import com.vsms.entity.SparePart;
import com.vsms.enums.JobStatus;
import com.vsms.enums.PaymentStatus;
import com.vsms.repository.InvoiceRepository;
import com.vsms.repository.MechanicRepository;
import com.vsms.repository.ServiceCatalogItemRepository;
import com.vsms.repository.ServiceJobRepository;
import com.vsms.repository.SparePartRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * One complete day in the workshop, driven through the real pages with real logins:
 * customer -> vehicle -> appointment -> job card -> mechanic works -> invoice -> payments -> delivery.
 */
@Transactional
class WorkflowWebTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ServiceCatalogItemRepository catalog;
    @Autowired
    MechanicRepository mechanics;
    @Autowired
    SparePartRepository parts;
    @Autowired
    ServiceJobRepository jobs;
    @Autowired
    InvoiceRepository invoices;

    @Test
    void fromNewCustomerToDeliveredVehicle() throws Exception {
        MockHttpSession advisor = login(mvc, "advisor");
        MockHttpSession ravi = login(mvc, "ravi.k");
        MockHttpSession meena = login(mvc, "meena.j");

        // ---- customer and vehicle
        Long customerId = idFromRedirect(mvc.perform(post("/customers").session(advisor).with(csrf())
                        .param("name", "Workflow Customer").param("phone", "9811122233").param("email", "wf@example.com"))
                .andExpect(status().is3xxRedirection()).andReturn());
        Long vehicleId = idFromRedirect(mvc.perform(post("/vehicles").session(advisor).with(csrf())
                        .param("registrationNumber", "WF 01 AA 0001").param("manufacturer", "Tata").param("model", "Punch")
                        .param("manufacturingYear", "2022").param("fuelType", "PETROL").param("customerId", customerId.toString()))
                .andExpect(status().is3xxRedirection()).andReturn());

        // a second vehicle with the same number is refused with a message next to the field
        mvc.perform(post("/vehicles").session(advisor).with(csrf())
                        .param("registrationNumber", "wf-01-aa-0001").param("manufacturer", "Tata").param("model", "Tiago")
                        .param("manufacturingYear", "2021").param("fuelType", "PETROL").param("customerId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already registered")));

        // ---- appointment, and a clashing second booking
        Long serviceId = catalog.findByNameIgnoreCase("Full Service").orElseThrow().getId();
        String day = LocalDate.now().plusDays(10).toString();
        Long appointmentId = idFromRedirect(mvc.perform(post("/appointments").session(advisor).with(csrf())
                        .param("customerId", customerId.toString()).param("vehicleId", vehicleId.toString())
                        .param("serviceItemId", serviceId.toString()).param("appointmentDate", day)
                        .param("appointmentTime", "12:00").param("notes", "Full service please"))
                .andExpect(status().is3xxRedirection()).andReturn());

        Long otherVehicle = idFromRedirect(mvc.perform(post("/vehicles").session(advisor).with(csrf())
                        .param("registrationNumber", "WF01AA0002").param("manufacturer", "Tata").param("model", "Tiago")
                        .param("manufacturingYear", "2021").param("fuelType", "PETROL").param("customerId", customerId.toString()))
                .andExpect(status().is3xxRedirection()).andReturn());
        mvc.perform(post("/appointments").session(advisor).with(csrf())
                        .param("customerId", customerId.toString()).param("vehicleId", otherVehicle.toString())
                        .param("serviceItemId", serviceId.toString()).param("appointmentDate", day).param("appointmentTime", "12:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already booked")));

        // ---- job card for the first appointment, assigned to Ravi
        Long raviId = mechanics.findAll().stream().filter(m -> m.getName().equals("Ravi Kulkarni")).findFirst().orElseThrow().getId();
        mvc.perform(get("/jobs/new").param("appointmentId", appointmentId.toString()).session(advisor))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Full service please")));
        Long jobId = idFromRedirect(mvc.perform(post("/jobs").session(advisor).with(csrf())
                        .param("appointmentId", appointmentId.toString()).param("customerComplaint", "Full service please")
                        .param("laborCharge", "1000.00").param("mechanicId", raviId.toString()))
                .andExpect(status().is3xxRedirection()).andReturn());
        assertEquals(JobStatus.ASSIGNED, jobs.findById(jobId).orElseThrow().getStatus());

        // ---- the wrong mechanic is locked out, the right one is not
        mvc.perform(get("/jobs/" + jobId).session(meena)).andExpect(status().isForbidden());
        mvc.perform(post("/jobs/" + jobId + "/status").session(meena).with(csrf()).param("target", "IN_PROGRESS"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/jobs/" + jobId).session(ravi)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Start work")));
        mvc.perform(get("/invoices").session(ravi)).andExpect(status().isForbidden());

        // ---- mechanic works the job
        mvc.perform(post("/jobs/" + jobId + "/status").session(ravi).with(csrf()).param("target", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/jobs/" + jobId + "/tasks").session(ravi).with(csrf()).param("description", "Change oil"))
                .andExpect(status().is3xxRedirection());
        SparePart oil = parts.findBySku("OIL-5W30-1L").orElseThrow();
        int stockBefore = oil.getStockQuantity();
        // asking for more than is in stock is refused and nothing changes
        mvc.perform(post("/jobs/" + jobId + "/parts").session(ravi).with(csrf())
                        .param("sparePartId", oil.getId().toString()).param("quantity", String.valueOf(stockBefore + 1)))
                .andExpect(status().is3xxRedirection());
        assertEquals(stockBefore, parts.findById(oil.getId()).orElseThrow().getStockQuantity());
        mvc.perform(post("/jobs/" + jobId + "/parts").session(ravi).with(csrf())
                        .param("sparePartId", oil.getId().toString()).param("quantity", "2"))
                .andExpect(status().is3xxRedirection());
        assertEquals(stockBefore - 2, parts.findById(oil.getId()).orElseThrow().getStockQuantity());
        mvc.perform(post("/jobs/" + jobId + "/status").session(ravi).with(csrf()).param("target", "COMPLETED"))
                .andExpect(status().is3xxRedirection());
        assertEquals(JobStatus.COMPLETED, jobs.findById(jobId).orElseThrow().getStatus());

        // ---- advisor invoices it: 1000.00 labour + 2 x 520.00 oil = 2040.00; 18% tax = 367.20; total 2407.20
        mvc.perform(get("/invoices/new").param("jobId", jobId.toString()).session(advisor))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Generate invoice")));
        Long invoiceId = idFromRedirect(mvc.perform(post("/invoices").session(advisor).with(csrf())
                        .param("jobId", jobId.toString()).param("taxPercent", "18"))
                .andExpect(status().is3xxRedirection()).andReturn());
        Invoice invoice = invoices.findById(invoiceId).orElseThrow();
        assertEquals(new BigDecimal("2407.20"), invoice.getGrandTotal());
        assertEquals(JobStatus.INVOICED, jobs.findById(jobId).orElseThrow().getStatus());
        // a second invoice for the same job is refused
        mvc.perform(post("/invoices").session(advisor).with(csrf()).param("jobId", jobId.toString()).param("taxPercent", "18"))
                .andExpect(status().is3xxRedirection());
        assertEquals(1, invoices.findAll().stream().filter(i -> i.getServiceJob().getId().equals(jobId)).count());
        mvc.perform(get("/invoices/" + invoiceId).session(advisor)).andExpect(status().isOk())
                .andExpect(content().string(containsString("₹2,407.20"))).andExpect(content().string(containsString("INVOICE")));

        // ---- cannot hand over the vehicle before it is paid
        mvc.perform(post("/jobs/" + jobId + "/status").session(advisor).with(csrf()).param("target", "DELIVERED"))
                .andExpect(status().is3xxRedirection());
        assertEquals(JobStatus.INVOICED, jobs.findById(jobId).orElseThrow().getStatus());

        // ---- payments: partial, rejected excess, rejected negative, final
        mvc.perform(post("/payments").session(advisor).with(csrf()).param("invoiceId", invoiceId.toString())
                        .param("amount", "1000.00").param("method", "CASH"))
                .andExpect(status().is3xxRedirection());
        assertEquals(PaymentStatus.PARTIALLY_PAID, invoices.findById(invoiceId).orElseThrow().getPaymentStatus());
        mvc.perform(post("/payments").session(advisor).with(csrf()).param("invoiceId", invoiceId.toString())
                        .param("amount", "2000.00").param("method", "CASH"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("more than the outstanding balance")));
        mvc.perform(post("/payments").session(advisor).with(csrf()).param("invoiceId", invoiceId.toString())
                        .param("amount", "-5").param("method", "CASH"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("must be greater than zero")));
        assertEquals(new BigDecimal("1000.00"), invoices.findById(invoiceId).orElseThrow().getAmountPaid());
        mvc.perform(post("/payments").session(advisor).with(csrf()).param("invoiceId", invoiceId.toString())
                        .param("amount", "1407.20").param("method", "UPI").param("reference", "UPI-TEST-1"))
                .andExpect(status().is3xxRedirection());
        assertEquals(PaymentStatus.PAID, invoices.findById(invoiceId).orElseThrow().getPaymentStatus());

        // ---- delivery
        mvc.perform(post("/jobs/" + jobId + "/status").session(advisor).with(csrf()).param("target", "DELIVERED"))
                .andExpect(status().is3xxRedirection());
        ServiceJob delivered = jobs.findById(jobId).orElseThrow();
        assertEquals(JobStatus.DELIVERED, delivered.getStatus());
        assertNotNull(delivered.getDeliveredAt());
        mvc.perform(get("/jobs/" + jobId).session(advisor)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Delivered")));

        // ---- the reports and dashboard now include it
        mvc.perform(get("/reports/payments").param("q", "Workflow Customer").session(advisor))
                .andExpect(status().isOk()).andExpect(content().string(containsString("UPI")));
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/").session(advisor)).andExpect(status().isOk());
    }
}
