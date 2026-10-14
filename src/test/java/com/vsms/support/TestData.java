package com.vsms.support;

import com.vsms.dto.AppointmentForm;
import com.vsms.dto.JobCreateForm;
import com.vsms.dto.PaymentForm;
import com.vsms.entity.Appointment;
import com.vsms.entity.Customer;
import com.vsms.entity.Mechanic;
import com.vsms.entity.ServiceCatalogItem;
import com.vsms.entity.ServiceJob;
import com.vsms.entity.SparePart;
import com.vsms.entity.Vehicle;
import com.vsms.enums.AppointmentStatus;
import com.vsms.enums.FuelType;
import com.vsms.enums.JobStatus;
import com.vsms.enums.PaymentMethod;
import com.vsms.enums.Role;
import com.vsms.repository.AppointmentRepository;
import com.vsms.repository.CustomerRepository;
import com.vsms.repository.MechanicRepository;
import com.vsms.repository.ServiceCatalogItemRepository;
import com.vsms.repository.SparePartRepository;
import com.vsms.repository.VehicleRepository;
import com.vsms.security.Actor;
import com.vsms.service.ServiceJobService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Creates small, unique records for tests (names and numbers never clash with the sample data). */
@Component
public class TestData {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);

    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final ServiceCatalogItemRepository catalog;
    private final MechanicRepository mechanics;
    private final SparePartRepository parts;
    private final AppointmentRepository appointments;
    private final ServiceJobService jobService;

    public TestData(CustomerRepository customers, VehicleRepository vehicles, ServiceCatalogItemRepository catalog,
                    MechanicRepository mechanics, SparePartRepository parts, AppointmentRepository appointments,
                    ServiceJobService jobService) {
        this.customers = customers;
        this.vehicles = vehicles;
        this.catalog = catalog;
        this.mechanics = mechanics;
        this.parts = parts;
        this.appointments = appointments;
        this.jobService = jobService;
    }

    public static int next() {
        return SEQUENCE.incrementAndGet();
    }

    public Customer customer() {
        int n = next();
        Customer customer = new Customer();
        customer.setName("Test Customer " + n);
        customer.setPhone("90000" + n);
        customer.setEmail("test" + n + "@example.com");
        return customers.save(customer);
    }

    public Vehicle vehicle(Customer owner) {
        int n = next();
        Vehicle vehicle = new Vehicle();
        vehicle.setCustomer(owner);
        vehicle.setRegistrationNumber("TS" + n);
        vehicle.setManufacturer("Testmaker");
        vehicle.setModel("Model " + n);
        vehicle.setManufacturingYear(2020);
        vehicle.setFuelType(FuelType.PETROL);
        return vehicles.save(vehicle);
    }

    public ServiceCatalogItem service(String price) {
        ServiceCatalogItem item = new ServiceCatalogItem();
        item.setName("Test service " + next());
        item.setStandardPrice(new BigDecimal(price));
        return catalog.save(item);
    }

    public Mechanic mechanic() {
        Mechanic mechanic = new Mechanic();
        mechanic.setName("Test Mechanic " + next());
        mechanic.setSpecialization("Testing");
        return mechanics.save(mechanic);
    }

    public SparePart part(int stock, String price) {
        SparePart part = new SparePart();
        part.setSku("TS-" + next());
        part.setName("Test part " + part.getSku());
        part.setUnitPrice(new BigDecimal(price));
        part.setStockQuantity(stock);
        part.setReorderLevel(1);
        return parts.save(part);
    }

    /** A scheduled appointment (saved directly, so it does not take part in booking-conflict tests). */
    public Appointment scheduledAppointment() {
        Customer customer = customer();
        return scheduledAppointment(customer, vehicle(customer), service("1000.00"));
    }

    public Appointment scheduledAppointment(Customer customer, Vehicle vehicle, ServiceCatalogItem service) {
        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setVehicle(vehicle);
        appointment.setServiceItem(service);
        appointment.setAppointmentDate(LocalDate.now().plusDays(5));
        appointment.setAppointmentTime(LocalTime.of(9, 0).plusMinutes(30L * (next() % 16)));
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        return appointments.save(appointment);
    }

    public AppointmentForm appointmentForm(Customer customer, Vehicle vehicle, ServiceCatalogItem service,
                                           LocalDate date, LocalTime time) {
        AppointmentForm form = new AppointmentForm();
        form.setCustomerId(customer.getId());
        form.setVehicleId(vehicle.getId());
        form.setServiceItemId(service.getId());
        form.setAppointmentDate(date);
        form.setAppointmentTime(time);
        return form;
    }

    public JobCreateForm jobForm(Appointment appointment, Mechanic mechanic, String labor) {
        JobCreateForm form = new JobCreateForm();
        form.setAppointmentId(appointment.getId());
        form.setCustomerComplaint("Something is wrong");
        form.setLaborCharge(new BigDecimal(labor));
        form.setMechanicId(mechanic == null ? null : mechanic.getId());
        return form;
    }

    /** A job in the CREATED state with the given labour charge. */
    public ServiceJob job(String labor) {
        return jobService.createFromAppointment(jobForm(scheduledAppointment(), null, labor), staff());
    }

    /** A job that already has a mechanic (status ASSIGNED). */
    public ServiceJob assignedJob(Mechanic mechanic, String labor) {
        return jobService.createFromAppointment(jobForm(scheduledAppointment(), mechanic, labor), staff());
    }

    /** A job moved through ASSIGNED -> IN_PROGRESS -> COMPLETED. */
    public ServiceJob completedJob(String labor) {
        ServiceJob job = assignedJob(mechanic(), labor);
        jobService.changeStatus(job.getId(), JobStatus.IN_PROGRESS, staff());
        jobService.changeStatus(job.getId(), JobStatus.COMPLETED, staff());
        return job;
    }

    public PaymentForm paymentForm(Long invoiceId, String amount, PaymentMethod method) {
        PaymentForm form = new PaymentForm();
        form.setInvoiceId(invoiceId);
        form.setAmount(new BigDecimal(amount));
        form.setMethod(method);
        return form;
    }

    public Actor staff() {
        return new Actor(null, "advisor.test", "Advisor Test", Role.SERVICE_ADVISOR, null);
    }

    public Actor admin() {
        return new Actor(null, "admin.test", "Admin Test", Role.ADMIN, null);
    }

    public Actor mechanicActor(Mechanic mechanic) {
        return new Actor(null, "mech.test", mechanic.getName(), Role.MECHANIC, mechanic.getId());
    }
}
