package com.vsms.controller;

import com.vsms.dto.CustomerForm;
import com.vsms.entity.Customer;
import com.vsms.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customers")
@PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", customerService.search(q, page));
        model.addAttribute("q", q);
        return "customers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("customerForm", new CustomerForm());
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("customerForm") CustomerForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return formView(model, null);
        }
        Customer saved = customerService.create(form);
        redirect.addFlashAttribute("success", "Customer \"" + saved.getName() + "\" was added.");
        return "redirect:/customers/" + saved.getId();
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("details", customerService.details(id));
        return "customers/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Customer customer = customerService.get(id);
        model.addAttribute("customerForm", CustomerForm.from(customer));
        return formView(model, customer);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("customerForm") CustomerForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return formView(model, customerService.get(id));
        }
        Customer saved = customerService.update(id, form);
        redirect.addFlashAttribute("success", "Customer \"" + saved.getName() + "\" was updated.");
        return "redirect:/customers/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        String name = customerService.get(id).getName();
        customerService.delete(id);
        redirect.addFlashAttribute("success", "Customer \"" + name + "\" was deleted.");
        return "redirect:/customers";
    }

    private String formView(Model model, Customer existing) {
        model.addAttribute("customer", existing);
        return "customers/form";
    }
}
