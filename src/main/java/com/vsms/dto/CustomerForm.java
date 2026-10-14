package com.vsms.dto;

import com.vsms.entity.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerForm {

    @NotBlank(message = "Enter the customer's name.")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
    private String name;

    @NotBlank(message = "Enter a phone number.")
    @Pattern(regexp = "^(?=(?:\\D*\\d){7,15}\\D*$)\\+?\\d[\\d \\-]*\\d$",
            message = "Enter a valid phone number: 7 to 15 digits, optionally starting with +.")
    private String phone;

    @Email(message = "Enter a valid email address.")
    @Size(max = 120, message = "Email can have at most 120 characters.")
    private String email;

    @Size(max = 255, message = "Address can have at most 255 characters.")
    private String address;

    public static CustomerForm from(Customer customer) {
        CustomerForm form = new CustomerForm();
        form.name = customer.getName();
        form.phone = customer.getPhone();
        form.email = customer.getEmail();
        form.address = customer.getAddress();
        return form;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
