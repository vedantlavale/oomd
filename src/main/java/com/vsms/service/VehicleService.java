package com.vsms.service;

import com.vsms.dto.VehicleForm;
import com.vsms.entity.Customer;
import com.vsms.entity.ServiceJob;
import com.vsms.entity.Vehicle;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.repository.AppointmentRepository;
import com.vsms.repository.CustomerRepository;
import com.vsms.repository.ServiceJobRepository;
import com.vsms.repository.VehicleRepository;
import com.vsms.util.Paging;
import com.vsms.util.Texts;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VehicleService {

    private final VehicleRepository vehicles;
    private final CustomerRepository customers;
    private final AppointmentRepository appointments;
    private final ServiceJobRepository jobs;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicles, CustomerRepository customers,
                          AppointmentRepository appointments, ServiceJobRepository jobs, Clock clock) {
        this.vehicles = vehicles;
        this.customers = customers;
        this.appointments = appointments;
        this.jobs = jobs;
        this.clock = clock;
    }

    /** A vehicle with its service history (used by the detail page). */
    public record VehicleDetails(Vehicle vehicle, List<ServiceJob> jobs, long appointmentCount) {
        public boolean isDeletable() {
            return appointmentCount == 0;
        }
    }

    @Transactional(readOnly = true)
    public Page<Vehicle> search(String query, int page) {
        return vehicles.search(Texts.like(query), Texts.likeRegistration(query),
                Paging.of(page, Sort.by("registrationNumber")));
    }

    @Transactional(readOnly = true)
    public List<Vehicle> findAll() {
        return vehicles.findAllByOrderByRegistrationNumber();
    }

    @Transactional(readOnly = true)
    public long count() {
        return vehicles.count();
    }

    @Transactional(readOnly = true)
    public Vehicle get(Long id) {
        return vehicles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Vehicle", id));
    }

    @Transactional(readOnly = true)
    public VehicleDetails details(Long id) {
        Vehicle vehicle = get(id);
        return new VehicleDetails(vehicle, jobs.findTop20ByAppointmentVehicleIdOrderByIdDesc(id),
                appointments.countByVehicleId(id));
    }

    public Vehicle create(VehicleForm form) {
        Vehicle vehicle = new Vehicle();
        apply(vehicle, form, false);
        return saveChecked(vehicle);
    }

    public Vehicle update(Long id, VehicleForm form) {
        Vehicle vehicle = get(id);
        apply(vehicle, form, true);
        return saveChecked(vehicle);
    }

    /** A vehicle that has appointments (and therefore possibly jobs and invoices) is never deleted. */
    public void delete(Long id) {
        Vehicle vehicle = get(id);
        long appointmentCount = appointments.countByVehicleId(id);
        if (appointmentCount > 0) {
            throw new BusinessRuleException(("%s cannot be deleted because it has %d appointment(s) on record. "
                    + "Vehicles with service history are kept so jobs and invoices stay correct.")
                    .formatted(vehicle.getRegistrationNumber(), appointmentCount));
        }
        vehicles.delete(vehicle);
    }

    private void apply(Vehicle vehicle, VehicleForm form, boolean existing) {
        String registration = Texts.normalizeRegistration(form.getRegistrationNumber());
        if (registration == null || !registration.matches("[A-Z0-9]{4,15}")) {
            throw new BusinessRuleException("registrationNumber",
                    "Use letters and digits only (4 to 15 characters), for example MH12AB1234.");
        }
        Optional<Vehicle> duplicate = vehicles.findByRegistrationNumber(registration);
        if (duplicate.isPresent() && (!existing || !duplicate.get().getId().equals(vehicle.getId()))) {
            throw new BusinessRuleException("registrationNumber",
                    "A vehicle with registration number " + registration + " is already registered.");
        }
        int latestYear = LocalDate.now(clock).getYear() + 1;
        if (form.getManufacturingYear() > latestYear) {
            throw new BusinessRuleException("manufacturingYear",
                    "Manufacturing year cannot be later than " + latestYear + ".");
        }
        Customer owner = customers.findById(form.getCustomerId())
                .orElseThrow(() -> new BusinessRuleException("customerId", "Choose an existing customer."));
        if (existing && vehicle.getCustomer() != null && !vehicle.getCustomer().getId().equals(owner.getId())
                && appointments.countByVehicleId(vehicle.getId()) > 0) {
            throw new BusinessRuleException("customerId",
                    "The owner cannot be changed because this vehicle already has appointments.");
        }
        vehicle.setRegistrationNumber(registration);
        vehicle.setManufacturer(Texts.clean(form.getManufacturer()));
        vehicle.setModel(Texts.clean(form.getModel()));
        vehicle.setManufacturingYear(form.getManufacturingYear());
        vehicle.setFuelType(form.getFuelType());
        vehicle.setCustomer(owner);
    }

    /** Flushes immediately so the database's unique constraint is checked inside this method. */
    private Vehicle saveChecked(Vehicle vehicle) {
        try {
            return vehicles.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("registrationNumber",
                    "A vehicle with registration number " + vehicle.getRegistrationNumber() + " is already registered.");
        }
    }
}
