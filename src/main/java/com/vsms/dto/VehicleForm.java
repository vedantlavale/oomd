package com.vsms.dto;

import com.vsms.entity.Vehicle;
import com.vsms.enums.FuelType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VehicleForm {

    @NotBlank(message = "Enter the registration number.")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 \\-]{2,18}[A-Za-z0-9]$",
            message = "Use letters and digits only (4 to 15 characters), for example MH12AB1234.")
    private String registrationNumber;

    @NotBlank(message = "Enter the manufacturer.")
    @Size(max = 60, message = "Manufacturer can have at most 60 characters.")
    private String manufacturer;

    @NotBlank(message = "Enter the model.")
    @Size(max = 60, message = "Model can have at most 60 characters.")
    private String model;

    @NotNull(message = "Enter the manufacturing year.")
    @Min(value = 1950, message = "Manufacturing year must be 1950 or later.")
    @Max(value = 2100, message = "Enter a valid manufacturing year.")
    private Integer manufacturingYear;

    @NotNull(message = "Choose the fuel type.")
    private FuelType fuelType;

    @NotNull(message = "Choose the vehicle owner.")
    private Long customerId;

    public static VehicleForm from(Vehicle vehicle) {
        VehicleForm form = new VehicleForm();
        form.registrationNumber = vehicle.getRegistrationNumber();
        form.manufacturer = vehicle.getManufacturer();
        form.model = vehicle.getModel();
        form.manufacturingYear = vehicle.getManufacturingYear();
        form.fuelType = vehicle.getFuelType();
        form.customerId = vehicle.getCustomer().getId();
        return form;
    }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getManufacturingYear() { return manufacturingYear; }
    public void setManufacturingYear(Integer manufacturingYear) { this.manufacturingYear = manufacturingYear; }
    public FuelType getFuelType() { return fuelType; }
    public void setFuelType(FuelType fuelType) { this.fuelType = fuelType; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
}
