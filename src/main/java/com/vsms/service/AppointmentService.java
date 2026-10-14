package com.vsms.service;

import com.vsms.config.VsmsProperties;
import com.vsms.dto.AppointmentForm;
import com.vsms.dto.RescheduleForm;
import com.vsms.entity.Appointment;
import com.vsms.entity.Customer;
import com.vsms.entity.ServiceCatalogItem;
import com.vsms.entity.ServiceJob;
import com.vsms.entity.Vehicle;
import com.vsms.enums.AppointmentStatus;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.repository.AppointmentRepository;
import com.vsms.repository.CustomerRepository;
import com.vsms.repository.ServiceCatalogItemRepository;
import com.vsms.repository.ServiceJobRepository;
import com.vsms.repository.VehicleRepository;
import com.vsms.util.Paging;
import com.vsms.util.Texts;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppointmentService {

    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_TEXT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final AppointmentRepository appointments;
    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final ServiceCatalogItemRepository catalog;
    private final ServiceJobRepository jobs;
    private final VsmsProperties.Appointments rules;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, CustomerRepository customers,
                              VehicleRepository vehicles, ServiceCatalogItemRepository catalog,
                              ServiceJobRepository jobs, VsmsProperties properties, Clock clock) {
        this.appointments = appointments;
        this.customers = customers;
        this.vehicles = vehicles;
        this.catalog = catalog;
        this.jobs = jobs;
        this.rules = properties.appointments();
        this.clock = clock;
    }

    /** An appointment together with its job card (if one was already created). */
    public record AppointmentDetails(Appointment appointment, ServiceJob job) {
    }

    @Transactional(readOnly = true)
    public Page<Appointment> search(String query, AppointmentStatus status, LocalDate from, LocalDate to,
                                    Long serviceId, int page) {
        return appointments.search(Texts.like(query), Texts.likeRegistration(query), status, from, to, serviceId,
                Paging.of(page, Sort.by(Sort.Order.desc("appointmentDate"), Sort.Order.desc("appointmentTime"),
                        Sort.Order.desc("id"))));
    }

    @Transactional(readOnly = true)
    public Appointment get(Long id) {
        return appointments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }

    @Transactional(readOnly = true)
    public AppointmentDetails details(Long id) {
        Appointment appointment = get(id);
        return new AppointmentDetails(appointment, jobs.findByAppointmentId(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<Appointment> today() {
        return appointments.findByAppointmentDateAndStatusInOrderByAppointmentTime(
                LocalDate.now(clock), AppointmentStatus.NOT_CANCELLED);
    }

    @Transactional(readOnly = true)
    public long countToday() {
        return appointments.countByAppointmentDateAndStatusIn(LocalDate.now(clock), AppointmentStatus.NOT_CANCELLED);
    }

    /** All bookable start times of a day, e.g. 09:00, 09:30, ... 17:30. */
    public List<LocalTime> timeSlots() {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime slot = rules.openingTime();
        while (!slot.plusMinutes(rules.slotMinutes()).isAfter(rules.closingTime())
                && slot.plusMinutes(rules.slotMinutes()).isAfter(slot)) {
            slots.add(slot);
            slot = slot.plusMinutes(rules.slotMinutes());
        }
        return slots;
    }

    public LocalDate currentDate() {
        return LocalDate.now(clock);
    }

    public LocalDate latestBookingDate() {
        return LocalDate.now(clock).plusDays(rules.maxDaysAhead());
    }

    /** Start times of one service on one day that are already taken (for greying out slots). */
    @Transactional(readOnly = true)
    public List<LocalTime> takenTimes(Long serviceId, LocalDate date) {
        return appointments.findTakenTimes(serviceId, date, AppointmentStatus.ACTIVE);
    }

    public Appointment book(AppointmentForm form) {
        // Lock order is always vehicle first, then service, so concurrent bookings cannot deadlock.
        Vehicle vehicle = vehicles.findByIdForUpdate(form.getVehicleId())
                .orElseThrow(() -> new BusinessRuleException("vehicleId", "Choose an existing vehicle."));
        ServiceCatalogItem service = catalog.findByIdForUpdate(form.getServiceItemId())
                .orElseThrow(() -> new BusinessRuleException("serviceItemId", "Choose an existing service."));
        Customer customer = customers.findById(form.getCustomerId())
                .orElseThrow(() -> new BusinessRuleException("customerId", "Choose an existing customer."));

        if (!vehicle.getCustomer().getId().equals(customer.getId())) {
            throw new BusinessRuleException("vehicleId", "This vehicle does not belong to the selected customer.");
        }
        if (!service.isActive()) {
            throw new BusinessRuleException("serviceItemId", "This service is not available for booking.");
        }
        validateSlot(form.getAppointmentDate(), form.getAppointmentTime());
        checkConflicts(service, vehicle, form.getAppointmentDate(), form.getAppointmentTime(), null);

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setVehicle(vehicle);
        appointment.setServiceItem(service);
        appointment.setAppointmentDate(form.getAppointmentDate());
        appointment.setAppointmentTime(form.getAppointmentTime());
        appointment.setNotes(Texts.clean(form.getNotes()));
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        return appointments.save(appointment);
    }

    public Appointment reschedule(Long id, RescheduleForm form) {
        Appointment appointment = get(id);
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new BusinessRuleException("Only scheduled appointments can be rescheduled. This one is "
                    + appointment.getStatus().getLabel().toLowerCase() + ".");
        }
        Vehicle vehicle = vehicles.findByIdForUpdate(appointment.getVehicle().getId()).orElseThrow();
        ServiceCatalogItem service = catalog.findByIdForUpdate(appointment.getServiceItem().getId()).orElseThrow();

        validateSlot(form.getAppointmentDate(), form.getAppointmentTime());
        checkConflicts(service, vehicle, form.getAppointmentDate(), form.getAppointmentTime(), appointment.getId());

        appointment.setAppointmentDate(form.getAppointmentDate());
        appointment.setAppointmentTime(form.getAppointmentTime());
        return appointment;
    }

    /** Only appointments that have no job card yet can be cancelled here; otherwise cancel the job. */
    public Appointment cancel(Long id) {
        Appointment appointment = get(id);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessRuleException("This appointment is already cancelled.");
        }
        if (appointment.getStatus() == AppointmentStatus.IN_PROGRESS) {
            throw new BusinessRuleException("A job card has been created for this appointment. "
                    + "Cancel the job card instead (possible until work starts).");
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException("A completed appointment cannot be cancelled.");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        return appointment;
    }

    private void validateSlot(LocalDate date, LocalTime time) {
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today)) {
            throw new BusinessRuleException("appointmentDate", "The appointment date cannot be in the past.");
        }
        if (date.isAfter(latestBookingDate())) {
            throw new BusinessRuleException("appointmentDate",
                    "Appointments can be booked at most " + rules.maxDaysAhead() + " days ahead.");
        }
        if (!timeSlots().contains(time)) {
            throw new BusinessRuleException("appointmentTime", "Choose one of the offered time slots between "
                    + rules.openingTime().format(TIME_TEXT) + " and " + rules.closingTime().format(TIME_TEXT) + ".");
        }
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("appointmentTime", "That time has already passed today. Choose a later slot.");
        }
    }

    private void checkConflicts(ServiceCatalogItem service, Vehicle vehicle, LocalDate date, LocalTime time,
                                Long excludeId) {
        if (appointments.countServiceSlotConflicts(service.getId(), date, time, AppointmentStatus.ACTIVE, excludeId) > 0) {
            throw new BusinessRuleException("appointmentTime", "The \"" + service.getName() + "\" slot at "
                    + time.format(TIME_TEXT) + " on " + date.format(DATE_TEXT) + " is already booked. Please choose another time.");
        }
        if (appointments.countVehicleSlotConflicts(vehicle.getId(), date, time, AppointmentStatus.ACTIVE, excludeId) > 0) {
            throw new BusinessRuleException("appointmentTime", vehicle.getRegistrationNumber()
                    + " already has an appointment at " + time.format(TIME_TEXT) + " on " + date.format(DATE_TEXT) + ".");
        }
    }
}
