package com.vsms.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsms.AbstractIntegrationTest;
import com.vsms.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CustomerWebTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    CustomerRepository customers;

    @Test
    void anonymousUserIsSentToLogin() throws Exception {
        mvc.perform(get("/customers"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(roles = "MECHANIC")
    void mechanicCannotOpenCustomers() throws Exception {
        mvc.perform(get("/customers")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SERVICE_ADVISOR")
    void advisorSeesCustomerList() throws Exception {
        mvc.perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Add customer")));
    }

    @Test
    @WithMockUser(roles = "SERVICE_ADVISOR")
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/customers").param("name", "No Token").param("phone", "9876543210"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SERVICE_ADVISOR")
    void validCustomerIsSavedAndNormalised() throws Exception {
        mvc.perform(post("/customers").with(csrf())
                        .param("name", "  Asha   Verma ")
                        .param("phone", "98765 43210")
                        .param("email", "Asha.Verma@Example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/customers/*"));
        var saved = customers.findAll().stream().filter(c -> c.getPhone().equals("98765 43210")).findFirst().orElseThrow();
        assertTrue(saved.getName().equals("Asha Verma"));
        assertTrue(saved.getEmail().equals("asha.verma@example.com"));
    }

    @Test
    @WithMockUser(roles = "SERVICE_ADVISOR")
    void invalidCustomerShowsMessagesAndIsNotSaved() throws Exception {
        long before = customers.count();
        mvc.perform(post("/customers").with(csrf())
                        .param("name", "A")
                        .param("phone", "12ab")
                        .param("email", "not-an-email"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Name must be between 2 and 100 characters.")))
                .andExpect(content().string(containsString("Enter a valid phone number")))
                .andExpect(content().string(containsString("Enter a valid email address.")));
        assertTrue(customers.count() == before);
    }

    @Test
    @WithMockUser(roles = "SERVICE_ADVISOR")
    void advisorCannotDeleteCustomers() throws Exception {
        var customer = new com.vsms.entity.Customer();
        customer.setName("Temp Person");
        customer.setPhone("9000000001");
        customer = customers.save(customer);
        mvc.perform(post("/customers/" + customer.getId() + "/delete").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanDeleteCustomerWithoutHistory() throws Exception {
        var customer = new com.vsms.entity.Customer();
        customer.setName("Temp Person");
        customer.setPhone("9000000002");
        customer = customers.save(customer);
        mvc.perform(post("/customers/" + customer.getId() + "/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers"));
        assertTrue(customers.findById(customer.getId()).isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownCustomerGives404Page() throws Exception {
        mvc.perform(get("/customers/999999")).andExpect(status().isNotFound());
    }
}
