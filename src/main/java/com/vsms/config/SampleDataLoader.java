package com.vsms.config;

import com.vsms.dto.JobCreateForm;
import com.vsms.dto.PaymentForm;
import com.vsms.entity.Appointment;
import com.vsms.entity.Customer;
import com.vsms.entity.Invoice;
import com.vsms.entity.Mechanic;
import com.vsms.entity.Payment;
import com.vsms.entity.ServiceCatalogItem;
import com.vsms.entity.ServiceJob;
import com.vsms.entity.SparePart;
import com.vsms.entity.UserAccount;
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
import com.vsms.service.AppointmentService;
import com.vsms.service.InvoiceService;
import com.vsms.service.PaymentService;
import com.vsms.service.ServiceJobService;
import com.vsms.service.UserAccountService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads a small fictional data set (customers, vehicles, mechanics, parts, appointments, jobs,
 * invoices and payments) the first time the application starts on an empty database, so every
 * screen has something to show. Everything is created through the real services, so totals,
 * stock levels and statuses are exactly what the application itself would produce.
 *
 * <p>Turn it off with VSMS_LOAD_SAMPLE_DATA=false. All names, phone numbers and e-mail addresses
 * are made up (e-mail addresses use the reserved domain example.com).
 */
@Component
@Order(2)
@ConditionalOnProperty(prefix = "vsms.sample-data", name = "enabled", havingValue = "true")
public class SampleDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);
    private static final BigDecimal TAX = new BigDecimal("18.00");

    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final ServiceCatalogItemRepository catalog;
    private final SparePartRepository parts;
    private final MechanicRepository mechanics;
    private final AppointmentRepository appointments;
    private final AppointmentService appointmentService;
    private final ServiceJobService jobService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final UserAccountService userService;
    private final AdminBootstrap.CredentialSource credentials;
    private final TransactionTemplate transaction;
    private final EntityManager entityManager;
    private final Clock clock;

    public SampleDataLoader(CustomerRepository customers, VehicleRepository vehicles,
                            ServiceCatalogItemRepository catalog, SparePartRepository parts,
                            MechanicRepository mechanics, AppointmentRepository appointments,
                            AppointmentService appointmentService, ServiceJobService jobService,
                            InvoiceService invoiceService, PaymentService paymentService,
                            UserAccountService userService, AdminBootstrap.CredentialSource credentials,
                            TransactionTemplate transaction, EntityManager entityManager, Clock clock) {
        this.customers = customers;
        this.vehicles = vehicles;
        this.catalog = catalog;
        this.parts = parts;
        this.mechanics = mechanics;
        this.appointments = appointments;
        this.appointmentService = appointmentService;
        this.jobService = jobService;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.userService = userService;
        this.credentials = credentials;
        this.transaction = transaction;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (customers.count() > 0 || catalog.count() > 0) {
            log.info("Sample data not loaded: the database already contains data.");
            return;
        }
        try {
            transaction.executeWithoutResult(status -> load());
            log.info("Sample data loaded (fictional customers, vehicles, jobs, invoices and payments).");
        } catch (RuntimeException ex) {
            log.error("Sample data could not be loaded; the application starts without it.", ex);
        }
    }

    private void load() {
        LocalDate today = LocalDate.now(clock);

        // ---------------------------------------------------------------- service catalogue
        ServiceCatalogItem basic = service("Basic Service", "Engine oil, filter and a 20-point check", "1499.00");
        ServiceCatalogItem full = service("Full Service", "Complete service with fluids, filters and inspection", "3999.00");
        ServiceCatalogItem oil = service("Oil & Filter Change", "Engine oil and oil filter replacement", "899.00");
        ServiceCatalogItem brakes = service("Brake Pad Replacement", "Replace worn brake pads and inspect discs", "1799.00");
        ServiceCatalogItem alignment = service("Wheel Alignment & Balancing", "Computerised alignment and wheel balancing", "999.00");
        ServiceCatalogItem ac = service("AC Service & Gas Top-up", "AC inspection, cleaning and gas refill", "1599.00");
        ServiceCatalogItem battery = service("Battery Check & Replacement", "Battery health test and replacement if needed", "499.00");
        ServiceCatalogItem diagnostics = service("Engine Diagnostics", "Scanner-based fault diagnosis", "799.00");

        // ------------------------------------------------------------------- spare parts
        SparePart engineOil = part("OIL-5W30-1L", "Engine Oil 5W-30 (1 L)", "520.00", 60, 15);
        SparePart oilFilter = part("FLT-OIL-01", "Oil Filter", "180.00", 40, 10);
        SparePart airFilter = part("FLT-AIR-01", "Air Filter", "320.00", 25, 8);
        SparePart frontPads = part("BRK-PAD-F", "Brake Pad Set (front)", "1450.00", 12, 4);
        part("BRK-PAD-R", "Brake Pad Set (rear)", "1250.00", 3, 4);
        part("SPK-PLG-01", "Spark Plug", "240.00", 48, 12);
        SparePart carBattery = part("BAT-12V-45", "Battery 12V 45Ah", "4200.00", 6, 2);
        part("WIP-BLD-01", "Wiper Blade (pair)", "350.00", 15, 5);
        part("CLT-1L", "Engine Coolant (1 L)", "280.00", 30, 10);
        SparePart acGas = part("AC-GAS-R134", "AC Gas R134a (100 g)", "150.00", 18, 20);
        part("BLB-H4", "Headlight Bulb H4", "210.00", 22, 6);
        part("BLT-FAN-01", "Fan Belt", "680.00", 9, 3);

        // --------------------------------------------------------------------- mechanics
        Mechanic ravi = mechanic("Ravi Kulkarni", "Engine and transmission", "9820022001", true);
        Mechanic meena = mechanic("Meena Joshi", "Electrical and AC", "9820022002", true);
        Mechanic imran = mechanic("Imran Shaikh", "Brakes and suspension", "9820022003", true);
        Mechanic pooja = mechanic("Pooja Nair", "General service", "9820022004", true);
        mechanic("Dinesh Rao", "Body and paint", "9820022005", false);

        // ---------------------------------------------------------------------- accounts
        String password = credentials.password("sample accounts: advisor, ravi.k, meena.j");
        UserAccount advisorUser = userService.createDirect("advisor", "Anita Deshmukh", Role.SERVICE_ADVISOR, null, password);
        UserAccount raviUser = userService.createDirect("ravi.k", "Ravi Kulkarni", Role.MECHANIC, ravi, password);
        UserAccount meenaUser = userService.createDirect("meena.j", "Meena Joshi", Role.MECHANIC, meena, password);
        Actor advisor = new Actor(advisorUser.getId(), advisorUser.getUsername(), advisorUser.getFullName(), Role.SERVICE_ADVISOR, null);
        Actor raviActor = new Actor(raviUser.getId(), raviUser.getUsername(), raviUser.getFullName(), Role.MECHANIC, ravi.getId());
        Actor meenaActor = new Actor(meenaUser.getId(), meenaUser.getUsername(), meenaUser.getFullName(), Role.MECHANIC, meena.getId());

        // --------------------------------------------------------- customers and vehicles
        Customer arjun = customer("Arjun Mehta", "9820011001", "arjun.mehta@example.com", "12 Lake View Apartments, Sample City");
        Customer priya = customer("Priya Nair", "9820011002", "priya.nair@example.com", "45 Green Park Road, Sample City");
        Customer rohan = customer("Rohan Desai", "9820011003", "rohan.desai@example.com", "7 Hill Crest Society, Sample City");
        Customer sneha = customer("Sneha Kulkarni", "9820011004", "sneha.kulkarni@example.com", "88 Market Street, Sample City");
        Customer vikram = customer("Vikram Singh", "9820011005", null, "21 Station Road, Sample City");
        Customer kavita = customer("Kavita Rao", "9820011006", "kavita.rao@example.com", "3 Palm Grove, Sample City");
        Customer imtiaz = customer("Imtiaz Qureshi", "9820011007", "imtiaz.q@example.com", "16 River Side, Sample City");
        Customer neha = customer("Neha Bansal", "9820011008", "neha.bansal@example.com", "92 Orchid Towers, Sample City");

        Vehicle swift = vehicle(arjun, "MH01AB1001", "Maruti Suzuki", "Swift VXi", 2019, FuelType.PETROL);
        Vehicle creta = vehicle(arjun, "MH01AB1002", "Hyundai", "Creta SX", 2021, FuelType.DIESEL);
        Vehicle city = vehicle(priya, "MH02CD2001", "Honda", "City ZX", 2018, FuelType.PETROL);
        Vehicle nexon = vehicle(rohan, "MH02CD2002", "Tata", "Nexon EV", 2023, FuelType.ELECTRIC);
        Vehicle innova = vehicle(sneha, "MH04GH4001", "Toyota", "Innova Crysta", 2020, FuelType.DIESEL);
        Vehicle wagonR = vehicle(vikram, "MH04GH4002", "Maruti Suzuki", "WagonR CNG", 2021, FuelType.CNG);
        Vehicle seltos = vehicle(kavita, "MH05JK5001", "Kia", "Seltos HTX", 2022, FuelType.PETROL);
        Vehicle polo = vehicle(imtiaz, "MH05JK5002", "Volkswagen", "Polo GT", 2016, FuelType.PETROL);
        Vehicle camry = vehicle(neha, "MH06LM6001", "Toyota", "Camry Hybrid", 2021, FuelType.HYBRID);
        Vehicle kwid = vehicle(neha, "MH06LM6002", "Renault", "Kwid RXT", 2019, FuelType.PETROL);

        // ----------------------------------------- job 1: delivered and fully paid (UPI)
        Appointment a1 = appointment(arjun, swift, full, today.minusDays(14), LocalTime.of(9, 30), "10,000 km service due");
        ServiceJob j1 = createJob(a1, pooja, "10,000 km service due. Slight vibration at idle.", advisor);
        jobService.changeStatus(j1.getId(), JobStatus.IN_PROGRESS, advisor);
        jobService.addTask(j1.getId(), "Replace engine oil and oil filter", advisor);
        jobService.addTask(j1.getId(), "Replace air filter", advisor);
        jobService.addTask(j1.getId(), "Check fluid levels and tyre pressure", advisor);
        for (var task : jobService.get(j1.getId(), advisor).getTasks()) {
            jobService.toggleTask(j1.getId(), task.getId(), advisor);
        }
        jobService.addPart(j1.getId(), engineOil.getId(), 4, advisor);
        jobService.addPart(j1.getId(), oilFilter.getId(), 1, advisor);
        jobService.addPart(j1.getId(), airFilter.getId(), 1, advisor);
        notes(j1, "Idle vibration traced to worn spark plug seating; cleaned and refitted.", "Service completed, road test done.", advisor);
        jobService.changeStatus(j1.getId(), JobStatus.COMPLETED, advisor);
        Invoice i1 = invoiceService.generate(j1.getId(), TAX, advisor);
        Payment p1 = pay(i1, i1.getGrandTotal(), PaymentMethod.UPI, "UPI-8841-2207", advisor);
        jobService.changeStatus(j1.getId(), JobStatus.DELIVERED, advisor);
        backdate(a1, j1, i1, p1, today.minusDays(14));

        // --------------------------------- job 2: invoiced, partly paid in cash
        Appointment a2 = appointment(priya, city, brakes, today.minusDays(9), LocalTime.of(10, 0), "Grinding noise when braking");
        ServiceJob j2 = createJob(a2, imran, "Grinding noise from the front brakes.", advisor);
        jobService.changeStatus(j2.getId(), JobStatus.IN_PROGRESS, advisor);
        jobService.addTask(j2.getId(), "Replace front brake pads", advisor);
        jobService.addTask(j2.getId(), "Skim front brake discs", advisor);
        jobService.addPart(j2.getId(), frontPads.getId(), 1, advisor);
        notes(j2, "Front pads worn to the metal; discs have light scoring.", "Pads replaced, discs skimmed, brakes bled.", advisor);
        jobService.changeStatus(j2.getId(), JobStatus.COMPLETED, advisor);
        Invoice i2 = invoiceService.generate(j2.getId(), TAX, advisor);
        Payment p2 = pay(i2, new BigDecimal("2000.00"), PaymentMethod.CASH, "Advance at pick-up", advisor);
        backdate(a2, j2, i2, p2, today.minusDays(9));

        // ------------------------------------------ job 3: invoiced, nothing paid yet
        Appointment a3 = appointment(rohan, nexon, battery, today.minusDays(6), LocalTime.of(11, 30), "Car shows low 12V battery warning");
        ServiceJob j3 = createJob(a3, meena, "12V auxiliary battery warning on the dashboard.", advisor);
        jobService.changeStatus(j3.getId(), JobStatus.IN_PROGRESS, advisor);
        jobService.addTask(j3.getId(), "Load-test the 12V battery", advisor);
        jobService.addTask(j3.getId(), "Replace battery and clear fault codes", advisor);
        jobService.addPart(j3.getId(), carBattery.getId(), 1, advisor);
        notes(j3, "Battery failed the load test (cells weak).", "Replaced battery, cleared warnings.", advisor);
        jobService.changeStatus(j3.getId(), JobStatus.COMPLETED, advisor);
        Invoice i3 = invoiceService.generate(j3.getId(), TAX, advisor);
        backdate(a3, j3, i3, null, today.minusDays(6));

        // ---------------------------------------- job 4: completed, waiting for invoice
        Appointment a4 = appointment(sneha, innova, alignment, today.minusDays(2), LocalTime.of(14, 0), "Steering pulls to the left");
        ServiceJob j4 = createJob(a4, imran, "Steering pulls to the left at highway speed.", advisor);
        jobService.changeStatus(j4.getId(), JobStatus.IN_PROGRESS, advisor);
        jobService.addTask(j4.getId(), "Wheel alignment (4 wheels)", advisor);
        jobService.addTask(j4.getId(), "Wheel balancing", advisor);
        for (var task : jobService.get(j4.getId(), advisor).getTasks()) {
            jobService.toggleTask(j4.getId(), task.getId(), advisor);
        }
        notes(j4, "Toe-in out of tolerance on both front wheels.", "Aligned and balanced; test drive fine.", advisor);
        jobService.changeStatus(j4.getId(), JobStatus.COMPLETED, advisor);
        backdate(a4, j4, null, null, today.minusDays(2));

        // --------------------------------- job 5: in progress today (mechanic account)
        Appointment a5 = appointment(vikram, wagonR, oil, today, LocalTime.of(9, 0), "Oil change before a long trip");
        ServiceJob j5 = createJob(a5, ravi, "Oil change before a long trip.", advisor);
        jobService.changeStatus(j5.getId(), JobStatus.IN_PROGRESS, raviActor);
        jobService.addTask(j5.getId(), "Drain old oil and replace filter", raviActor);
        jobService.addTask(j5.getId(), "Refill with 5W-30 and check level", raviActor);
        jobService.addPart(j5.getId(), engineOil.getId(), 3, raviActor);
        jobService.addPart(j5.getId(), oilFilter.getId(), 1, raviActor);

        // ------------------------------------ job 6: on hold (waiting for a part)
        Appointment a6 = appointment(kavita, seltos, ac, today, LocalTime.of(10, 30), "AC not cooling properly");
        ServiceJob j6 = createJob(a6, meena, "AC blows warm air after 10 minutes.", advisor);
        jobService.changeStatus(j6.getId(), JobStatus.IN_PROGRESS, meenaActor);
        jobService.addTask(j6.getId(), "Pressure test AC circuit", meenaActor);
        jobService.addTask(j6.getId(), "Refill AC gas", meenaActor);
        jobService.addPart(j6.getId(), acGas.getId(), 4, meenaActor);
        notes(j6, "Small leak at the condenser fitting; waiting for a replacement O-ring kit.", null, meenaActor);
        jobService.changeStatus(j6.getId(), JobStatus.ON_HOLD, meenaActor);

        // -------------------------------------------- job 7: assigned, not started
        Appointment a7 = appointment(imtiaz, polo, diagnostics, today, LocalTime.of(12, 0), "Check engine light is on");
        createJob(a7, ravi, "Check engine light came on yesterday.", advisor);

        // ------------------------------------- job 8: created, no mechanic yet
        Appointment a8 = appointment(arjun, creta, oil, today, LocalTime.of(15, 0), "Oil change");
        createJob(a8, null, "Oil and filter change.", advisor);

        // --------------------------- appointments without jobs: later today, future, cancelled
        appointment(neha, camry, basic, today, LocalTime.of(16, 30), "Hybrid battery check as well, please");
        appointment(arjun, swift, basic, today.plusDays(1), LocalTime.of(10, 0), null);
        appointment(priya, city, alignment, today.plusDays(2), LocalTime.of(11, 30), "Alignment after the brake job");
        Appointment cancelled = appointment(neha, kwid, full, today.plusDays(1), LocalTime.of(14, 0), null);
        appointmentService.cancel(cancelled.getId());
        entityManager.flush();
    }

    // ------------------------------------------------------------------ small builders

    private ServiceCatalogItem service(String name, String description, String price) {
        ServiceCatalogItem item = new ServiceCatalogItem();
        item.setName(name);
        item.setDescription(description);
        item.setStandardPrice(new BigDecimal(price));
        return catalog.save(item);
    }

    private SparePart part(String sku, String name, String price, int stock, int reorderLevel) {
        SparePart part = new SparePart();
        part.setSku(sku);
        part.setName(name);
        part.setUnitPrice(new BigDecimal(price));
        part.setStockQuantity(stock);
        part.setReorderLevel(reorderLevel);
        return parts.save(part);
    }

    private Mechanic mechanic(String name, String specialization, String phone, boolean active) {
        Mechanic mechanic = new Mechanic();
        mechanic.setName(name);
        mechanic.setSpecialization(specialization);
        mechanic.setPhone(phone);
        mechanic.setActive(active);
        return mechanics.save(mechanic);
    }

    private Customer customer(String name, String phone, String email, String address) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setEmail(email);
        customer.setAddress(address);
        return customers.save(customer);
    }

    private Vehicle vehicle(Customer owner, String registration, String manufacturer, String model, int year, FuelType fuel) {
        Vehicle vehicle = new Vehicle();
        vehicle.setCustomer(owner);
        vehicle.setRegistrationNumber(registration);
        vehicle.setManufacturer(manufacturer);
        vehicle.setModel(model);
        vehicle.setManufacturingYear(year);
        vehicle.setFuelType(fuel);
        return vehicles.save(vehicle);
    }

    /** Saves directly (not through the booking rules) so that appointments in the past can exist. */
    private Appointment appointment(Customer customer, Vehicle vehicle, ServiceCatalogItem service,
                                    LocalDate date, LocalTime time, String notes) {
        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setVehicle(vehicle);
        appointment.setServiceItem(service);
        appointment.setAppointmentDate(date);
        appointment.setAppointmentTime(time);
        appointment.setNotes(notes);
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        return appointments.save(appointment);
    }

    private ServiceJob createJob(Appointment appointment, Mechanic mechanic, String complaint, Actor actor) {
        JobCreateForm form = new JobCreateForm();
        form.setAppointmentId(appointment.getId());
        form.setCustomerComplaint(complaint);
        form.setLaborCharge(appointment.getServiceItem().getStandardPrice());
        form.setMechanicId(mechanic == null ? null : mechanic.getId());
        return jobService.createFromAppointment(form, actor);
    }

    private void notes(ServiceJob job, String diagnosis, String repairNotes, Actor actor) {
        var form = new com.vsms.dto.JobNotesForm();
        form.setDiagnosis(diagnosis);
        form.setRepairNotes(repairNotes);
        jobService.updateNotes(job.getId(), form, actor);
    }

    private Payment pay(Invoice invoice, BigDecimal amount, PaymentMethod method, String reference, Actor actor) {
        PaymentForm form = new PaymentForm();
        form.setInvoiceId(invoice.getId());
        form.setAmount(amount);
        form.setMethod(method);
        form.setReference(reference);
        return paymentService.record(form, actor);
    }

    /** Gives finished sample jobs believable dates in the past (the creation timestamp has no setter). */
    private void backdate(Appointment appointment, ServiceJob job, Invoice invoice, Payment payment, LocalDate day) {
        LocalDateTime opened = day.minusDays(2).atTime(16, 0);
        entityManager.flush();
        updateCreatedAt("appointments", appointment.getId(), opened);
        updateCreatedAt("service_jobs", job.getId(), day.atTime(appointment.getAppointmentTime()).minusMinutes(20));
        job.setStartedAt(day.atTime(appointment.getAppointmentTime()).plusMinutes(10));
        if (job.getCompletedAt() != null) {
            job.setCompletedAt(day.atTime(16, 30));
        }
        if (job.getDeliveredAt() != null) {
            job.setDeliveredAt(day.atTime(18, 0));
        }
        if (invoice != null) {
            invoice.setIssuedAt(day.atTime(17, 0));
            updateCreatedAt("invoices", invoice.getId(), day.atTime(17, 0));
        }
        if (payment != null) {
            payment.setPaidAt(day.atTime(17, 45));
            updateCreatedAt("payments", payment.getId(), day.atTime(17, 45));
        }
    }

    private void updateCreatedAt(String table, Long id, LocalDateTime when) {
        entityManager.createNativeQuery("update " + table + " set created_at = ?1 where id = ?2")
                .setParameter(1, when)
                .setParameter(2, id)
                .executeUpdate();
    }
}
