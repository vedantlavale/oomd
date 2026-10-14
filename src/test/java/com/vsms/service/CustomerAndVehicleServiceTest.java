package com.vsms.service;

import static com.vsms.support.Rules.assertRejected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vsms.AbstractIntegrationTest;
import com.vsms.dto.CustomerForm;
import com.vsms.dto.VehicleForm;
import com.vsms.entity.Customer;
import com.vsms.entity.Vehicle;
import com.vsms.enums.FuelType;
import com.vsms.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CustomerAndVehicleServiceTest extends AbstractIntegrationTest {

    @Autowired
    CustomerService customerService;
    @Autowired
    VehicleService vehicleService;
    @Autowired
    TestData data;

    private CustomerForm customerForm(String name, String phone) {
        CustomerForm form = new CustomerForm();
        form.setName(name);
        form.setPhone(phone);
        return form;
    }

    private VehicleForm vehicleForm(Long customerId, String registration) {
        VehicleForm form = new VehicleForm();
        form.setCustomerId(customerId);
        form.setRegistrationNumber(registration);
        form.setManufacturer("Maruti Suzuki");
        form.setModel("Swift");
        form.setManufacturingYear(2020);
        form.setFuelType(FuelType.PETROL);
        return form;
    }

    @Test
    void customerIsCreatedWithCleanedValues() {
        CustomerForm form = customerForm("  Meera   Iyer ", " 98765 43210 ");
        form.setEmail("  Meera@Example.COM ");
        Customer saved = customerService.create(form);

        assertEquals("Meera Iyer", saved.getName());
        assertEquals("98765 43210", saved.getPhone());
        assertEquals("meera@example.com", saved.getEmail());
        assertTrue(customerService.search("meera iyer", 0).getContent().stream().anyMatch(c -> c.getId().equals(saved.getId())));
    }

    @Test
    void customerWithoutHistoryCanBeDeleted() {
        Customer customer = data.customer();
        customerService.delete(customer.getId());
        assertTrue(customerService.search(customer.getName(), 0).isEmpty());
    }

    @Test
    void customerWithAVehicleCannotBeDeleted() {
        Customer customer = data.customer();
        data.vehicle(customer);
        assertRejected("cannot be deleted", () -> customerService.delete(customer.getId()));
    }

    @Test
    void customerWithAnAppointmentCannotBeDeleted() {
        var appointment = data.scheduledAppointment();
        assertRejected("1 appointment", () -> customerService.delete(appointment.getCustomer().getId()));
    }

    @Test
    void vehicleIsRegisteredAgainstACustomer() {
        Customer owner = data.customer();
        Vehicle saved = vehicleService.create(vehicleForm(owner.getId(), "mh 12-ab 1234"));
        assertEquals("MH12AB1234", saved.getRegistrationNumber());
        assertEquals(owner.getId(), saved.getCustomer().getId());
        assertEquals(1, vehicleService.search("mh12ab", 0).getContent().stream()
                .filter(v -> v.getId().equals(saved.getId())).count());
    }

    @Test
    void duplicateRegistrationNumbersAreRejectedWhateverTheFormatting() {
        Customer owner = data.customer();
        vehicleService.create(vehicleForm(owner.getId(), "KA01ZZ9999"));
        assertRejected("registrationNumber", "already registered",
                () -> vehicleService.create(vehicleForm(owner.getId(), "ka-01 zz 9999")));
    }

    @Test
    void registrationNumberOfAnotherVehicleCannotBeTakenOnEdit() {
        Customer owner = data.customer();
        vehicleService.create(vehicleForm(owner.getId(), "KA01YY8888"));
        Vehicle second = vehicleService.create(vehicleForm(owner.getId(), "KA01YY8889"));
        assertRejected("registrationNumber", "already registered",
                () -> vehicleService.update(second.getId(), vehicleForm(owner.getId(), "KA01YY8888")));
    }

    @Test
    void aVehicleCanKeepItsOwnRegistrationNumberWhenEdited() {
        Customer owner = data.customer();
        Vehicle vehicle = vehicleService.create(vehicleForm(owner.getId(), "KA01XX7777"));
        VehicleForm form = vehicleForm(owner.getId(), "KA01XX7777");
        form.setModel("Swift ZXi");
        assertEquals("Swift ZXi", vehicleService.update(vehicle.getId(), form).getModel());
    }

    @Test
    void invalidRegistrationAndYearAreRejected() {
        Customer owner = data.customer();
        assertRejected("registrationNumber", "letters and digits",
                () -> vehicleService.create(vehicleForm(owner.getId(), "A-!")));
        VehicleForm future = vehicleForm(owner.getId(), "KA01WW6666");
        future.setManufacturingYear(2099);
        assertRejected("manufacturingYear", "cannot be later", () -> vehicleService.create(future));
    }

    @Test
    void vehicleWithAppointmentsCannotBeDeletedOrGivenToAnotherCustomer() {
        var appointment = data.scheduledAppointment();
        Vehicle vehicle = appointment.getVehicle();
        assertRejected("cannot be deleted", () -> vehicleService.delete(vehicle.getId()));

        Customer other = data.customer();
        assertRejected("customerId", "owner cannot be changed",
                () -> vehicleService.update(vehicle.getId(), vehicleForm(other.getId(), vehicle.getRegistrationNumber())));
    }

    @Test
    void unusedVehicleCanBeDeleted() {
        Vehicle vehicle = data.vehicle(data.customer());
        vehicleService.delete(vehicle.getId());
        assertTrue(vehicleService.search(vehicle.getRegistrationNumber(), 0).isEmpty());
    }
}
