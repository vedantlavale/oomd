package com.vsms.entity;

import com.vsms.enums.FuelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "vehicles",
        uniqueConstraints = @UniqueConstraint(name = "uk_vehicle_registration", columnNames = "registration_number"),
        indexes = @Index(name = "idx_vehicle_customer", columnList = "customer_id"))
public class Vehicle extends AuditedEntity {

    /** Stored upper-case without spaces or hyphens, e.g. MH12AB1234. */
    @Column(name = "registration_number", nullable = false, length = 20)
    private String registrationNumber;

    @Column(nullable = false, length = 60)
    private String manufacturer;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(name = "manufacturing_year", nullable = false)
    private Integer manufacturingYear;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "fuel_type", nullable = false, length = 20)
    private FuelType fuelType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vehicle_customer"))
    private Customer customer;

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
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    /** Short label for lists and drop-downs, e.g. "MH12AB1234 - Maruti Swift". */
    public String getDisplayName() {
        return registrationNumber + " - " + manufacturer + " " + model;
    }
}
