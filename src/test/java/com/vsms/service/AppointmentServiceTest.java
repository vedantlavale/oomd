package com.vsms.service;

import static com.vsms.support.Rules.assertRejected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vsms.AbstractIntegrationTest;
import com.vsms.dto.RescheduleForm;
import com.vsms.entity.Appointment;
import com.vsms.entity.Customer;
import com.vsms.entity.ServiceCatalogItem;
import com.vsms.entity.Vehicle;
import com.vsms.enums.AppointmentStatus;
import com.vsms.support.TestData;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AppointmentServiceTest extends AbstractIntegrationTest {

    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Autowired
    AppointmentService appointmentService;
    @Autowired
    ServiceJobService jobService;
    @Autowired
    TestData data;

    private final LocalDate day = LocalDate.now().plusDays(3);

    private Appointment book(Customer c, Vehicle v, ServiceCatalogItem s, LocalDate date, LocalTime time) {
        return appointmentService.book(data.appointmentForm(c, v, s, date, time));
    }

    @Test
    void bookingCreatesAScheduledAppointment() {
        Customer customer = data.customer();
        Vehicle vehicle = data.vehicle(customer);
        ServiceCatalogItem service = data.service("1500.00");
        Appointment appointment = book(customer, vehicle, service, day, TEN);

        assertEquals(AppointmentStatus.SCHEDULED, appointment.getStatus());
        assertEquals(day, appointment.getAppointmentDate());
        assertEquals(TEN, appointment.getAppointmentTime());
        assertTrue(appointmentService.takenTimes(service.getId(), day).contains(TEN));
    }

    @Test
    void twoActiveAppointmentsCannotShareAServiceSlot() {
        ServiceCatalogItem service = data.service("1000.00");
        Customer first = data.customer();
        book(first, data.vehicle(first), service, day, TEN);

        Customer second = data.customer();
        assertRejected("appointmentTime", "already booked",
                () -> book(second, data.vehicle(second), service, day, TEN));
    }

    @Test
    void differentServicesMayUseTheSameTime() {
        Customer first = data.customer();
        book(first, data.vehicle(first), data.service("1000.00"), day, TEN);
        Customer second = data.customer();
        book(second, data.vehicle(second), data.service("1000.00"), day, TEN);
    }

    @Test
    void oneVehicleCannotBeBookedTwiceAtTheSameTime() {
        Customer customer = data.customer();
        Vehicle vehicle = data.vehicle(customer);
        book(customer, vehicle, data.service("1000.00"), day, TEN);
        assertRejected("appointmentTime", "already has an appointment",
                () -> book(customer, vehicle, data.service("1000.00"), day, TEN));
    }

    @Test
    void cancellingFreesTheSlotForSomeoneElse() {
        ServiceCatalogItem service = data.service("1000.00");
        Customer first = data.customer();
        Appointment booked = book(first, data.vehicle(first), service, day, TEN);
        appointmentService.cancel(booked.getId());
        assertEquals(AppointmentStatus.CANCELLED, appointmentService.get(booked.getId()).getStatus());

        Customer second = data.customer();
        book(second, data.vehicle(second), service, day, TEN);
    }

    @Test
    void pastDatesFarFutureDatesAndOddSlotsAreRejected() {
        Customer customer = data.customer();
        Vehicle vehicle = data.vehicle(customer);
        ServiceCatalogItem service = data.service("1000.00");

        assertRejected("appointmentDate", "in the past",
                () -> book(customer, vehicle, service, LocalDate.now().minusDays(1), TEN));
        assertRejected("appointmentDate", "days ahead",
                () -> book(customer, vehicle, service, LocalDate.now().plusDays(400), TEN));
        assertRejected("appointmentTime", "time slots",
                () -> book(customer, vehicle, service, day, LocalTime.of(8, 0)));
        assertRejected("appointmentTime", "time slots",
                () -> book(customer, vehicle, service, day, LocalTime.of(10, 15)));
        assertRejected("appointmentTime", "time slots",
                () -> book(customer, vehicle, service, day, LocalTime.of(18, 0)));
    }

    @Test
    void theVehicleMustBelongToTheCustomerAndTheServiceMustBeActive() {
        Customer owner = data.customer();
        Vehicle vehicle = data.vehicle(owner);
        Customer stranger = data.customer();
        assertRejected("vehicleId", "does not belong",
                () -> book(stranger, vehicle, data.service("1000.00"), day, TEN));

        ServiceCatalogItem retired = data.service("1000.00");
        retired.setActive(false);
        assertRejected("serviceItemId", "not available",
                () -> book(owner, vehicle, retired, day, TEN));
    }

    @Test
    void reschedulingMovesTheAppointmentAndChecksConflicts() {
        ServiceCatalogItem service = data.service("1000.00");
        Customer a = data.customer();
        Appointment first = book(a, data.vehicle(a), service, day, TEN);
        Customer b = data.customer();
        book(b, data.vehicle(b), service, day, LocalTime.of(11, 0));

        RescheduleForm toTakenSlot = new RescheduleForm();
        toTakenSlot.setAppointmentDate(day);
        toTakenSlot.setAppointmentTime(LocalTime.of(11, 0));
        assertRejected("appointmentTime", "already booked", () -> appointmentService.reschedule(first.getId(), toTakenSlot));

        RescheduleForm toFreeSlot = new RescheduleForm();
        toFreeSlot.setAppointmentDate(day.plusDays(1));
        toFreeSlot.setAppointmentTime(LocalTime.of(14, 30));
        Appointment moved = appointmentService.reschedule(first.getId(), toFreeSlot);
        assertEquals(day.plusDays(1), moved.getAppointmentDate());
        assertEquals(LocalTime.of(14, 30), moved.getAppointmentTime());

        // moving to the slot it already holds is not a conflict with itself
        RescheduleForm sameSlot = new RescheduleForm();
        sameSlot.setAppointmentDate(day.plusDays(1));
        sameSlot.setAppointmentTime(LocalTime.of(14, 30));
        appointmentService.reschedule(first.getId(), sameSlot);
    }

    @Test
    void onlyScheduledAppointmentsCanBeRescheduledOrCancelled() {
        Customer customer = data.customer();
        Appointment appointment = book(customer, data.vehicle(customer), data.service("1000.00"), day, TEN);
        appointmentService.cancel(appointment.getId());

        RescheduleForm form = new RescheduleForm();
        form.setAppointmentDate(day);
        form.setAppointmentTime(LocalTime.of(12, 0));
        assertRejected("scheduled appointments", () -> appointmentService.reschedule(appointment.getId(), form));
        assertRejected("already cancelled", () -> appointmentService.cancel(appointment.getId()));
    }

    @Test
    void anAppointmentWithAJobCardMustBeCancelledThroughTheJob() {
        Appointment appointment = data.scheduledAppointment();
        jobService.createFromAppointment(data.jobForm(appointment, null, "500.00"), data.staff());
        assertEquals(AppointmentStatus.IN_PROGRESS, appointmentService.get(appointment.getId()).getStatus());
        assertRejected("job card", () -> appointmentService.cancel(appointment.getId()));
    }

    @Test
    void timeSlotsFollowTheOpeningHours() {
        var slots = appointmentService.timeSlots();
        assertEquals(LocalTime.of(9, 0), slots.get(0));
        assertEquals(LocalTime.of(17, 30), slots.get(slots.size() - 1));
        assertEquals(18, slots.size());
    }
}
