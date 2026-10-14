package com.vsms.service;

import com.vsms.dto.CustomerForm;
import com.vsms.entity.Customer;
import com.vsms.entity.Vehicle;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.repository.AppointmentRepository;
import com.vsms.repository.CustomerRepository;
import com.vsms.repository.VehicleRepository;
import com.vsms.util.Paging;
import com.vsms.util.Texts;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final AppointmentRepository appointments;

    public CustomerService(CustomerRepository customers, VehicleRepository vehicles,
                           AppointmentRepository appointments) {
        this.customers = customers;
        this.vehicles = vehicles;
        this.appointments = appointments;
    }

    /** A customer together with what is attached to them (used by the detail page). */
    public record CustomerDetails(Customer customer, List<Vehicle> vehicles, long appointmentCount) {
        public boolean isDeletable() {
            return vehicles.isEmpty() && appointmentCount == 0;
        }
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(String query, int page) {
        return customers.search(Texts.like(query), Paging.of(page, Sort.by("name", "id")));
    }

    @Transactional(readOnly = true)
    public List<Customer> findAll() {
        return customers.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public long count() {
        return customers.count();
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    @Transactional(readOnly = true)
    public CustomerDetails details(Long id) {
        Customer customer = get(id);
        return new CustomerDetails(customer, vehicles.findByCustomerIdOrderByRegistrationNumber(id),
                appointments.countByCustomerId(id));
    }

    public Customer create(CustomerForm form) {
        Customer customer = new Customer();
        apply(customer, form);
        return customers.save(customer);
    }

    public Customer update(Long id, CustomerForm form) {
        Customer customer = get(id);
        apply(customer, form);
        return customer;
    }

    /** Deletes a customer only when nothing (vehicles, appointments, jobs, invoices) refers to them. */
    public void delete(Long id) {
        Customer customer = get(id);
        long vehicleCount = vehicles.countByCustomerId(id);
        long appointmentCount = appointments.countByCustomerId(id);
        if (vehicleCount > 0 || appointmentCount > 0) {
            throw new BusinessRuleException(("%s cannot be deleted: %d vehicle(s) and %d appointment(s) are linked "
                    + "to this customer. Customers with history are kept so past jobs and invoices stay correct.")
                    .formatted(customer.getName(), vehicleCount, appointmentCount));
        }
        customers.delete(customer);
    }

    private void apply(Customer customer, CustomerForm form) {
        customer.setName(Texts.clean(form.getName()));
        customer.setPhone(Texts.clean(form.getPhone()));
        String email = Texts.trimToNull(form.getEmail());
        customer.setEmail(email == null ? null : email.toLowerCase(Locale.ROOT));
        customer.setAddress(Texts.clean(form.getAddress()));
    }
}
