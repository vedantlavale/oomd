package com.vsms.controller;

import com.vsms.dto.VehicleForm;
import com.vsms.entity.Vehicle;
import com.vsms.enums.FuelType;
import com.vsms.exception.BusinessRuleException;
import com.vsms.service.CustomerService;
import com.vsms.service.VehicleService;
import com.vsms.util.FormErrors;
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
@RequestMapping("/vehicles")
@PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
public class VehicleController {

    private final VehicleService vehicleService;
    private final CustomerService customerService;

    public VehicleController(VehicleService vehicleService, CustomerService customerService) {
        this.vehicleService = vehicleService;
        this.customerService = customerService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", vehicleService.search(q, page));
        model.addAttribute("q", q);
        return "vehicles/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long customerId, Model model) {
        VehicleForm form = new VehicleForm();
        form.setCustomerId(customerId);
        model.addAttribute("vehicleForm", form);
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("vehicleForm") VehicleForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                Vehicle saved = vehicleService.create(form);
                redirect.addFlashAttribute("success", "Vehicle " + saved.getRegistrationNumber() + " was registered.");
                return "redirect:/vehicles/" + saved.getId();
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        return formView(model, null);
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("details", vehicleService.details(id));
        return "vehicles/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Vehicle vehicle = vehicleService.get(id);
        model.addAttribute("vehicleForm", VehicleForm.from(vehicle));
        return formView(model, vehicle);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("vehicleForm") VehicleForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                Vehicle saved = vehicleService.update(id, form);
                redirect.addFlashAttribute("success", "Vehicle " + saved.getRegistrationNumber() + " was updated.");
                return "redirect:/vehicles/" + id;
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        return formView(model, vehicleService.get(id));
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        String registration = vehicleService.get(id).getRegistrationNumber();
        vehicleService.delete(id);
        redirect.addFlashAttribute("success", "Vehicle " + registration + " was deleted.");
        return "redirect:/vehicles";
    }

    private String formView(Model model, Vehicle existing) {
        model.addAttribute("vehicle", existing);
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("fuelTypes", FuelType.values());
        return "vehicles/form";
    }
}
