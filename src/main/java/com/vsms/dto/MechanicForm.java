package com.vsms.dto;

import com.vsms.entity.Mechanic;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class MechanicForm {

    @NotBlank(message = "Enter the mechanic's name.")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
    private String name;

    @NotBlank(message = "Enter the specialization, for example Engine or Brakes.")
    @Size(max = 100, message = "Specialization can have at most 100 characters.")
    private String specialization;

    @Pattern(regexp = "^$|^(?=(?:\\D*\\d){7,15}\\D*$)\\+?\\d[\\d \\-]*\\d$",
            message = "Enter a valid phone number: 7 to 15 digits, optionally starting with +.")
    private String phone;

    public static MechanicForm from(Mechanic mechanic) {
        MechanicForm form = new MechanicForm();
        form.name = mechanic.getName();
        form.specialization = mechanic.getSpecialization();
        form.phone = mechanic.getPhone();
        return form;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
