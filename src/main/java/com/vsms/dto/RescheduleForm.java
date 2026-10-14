package com.vsms.dto;

import com.vsms.entity.Appointment;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

public class RescheduleForm {

    @NotNull(message = "Choose the new date.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate appointmentDate;

    @NotNull(message = "Choose a time slot.")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime appointmentTime;

    public static RescheduleForm from(Appointment appointment) {
        RescheduleForm form = new RescheduleForm();
        form.appointmentDate = appointment.getAppointmentDate();
        form.appointmentTime = appointment.getAppointmentTime();
        return form;
    }

    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public LocalTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalTime appointmentTime) { this.appointmentTime = appointmentTime; }
}
