package com.vsms.controller;

import com.vsms.dto.CatalogItemForm;
import com.vsms.entity.ServiceCatalogItem;
import com.vsms.exception.BusinessRuleException;
import com.vsms.service.ServiceCatalogService;
import com.vsms.util.Filters;
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

/** The list of services the workshop offers. Staff can look; administrators change it. */
@Controller
@RequestMapping("/catalog")
@PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
public class ServiceCatalogController {

    private final ServiceCatalogService catalogService;

    public ServiceCatalogController(ServiceCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", catalogService.search(q, Filters.active(status), page));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        return "catalog/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newForm(Model model) {
        model.addAttribute("catalogItemForm", new CatalogItemForm());
        return formView(model, null);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String create(@Valid @ModelAttribute("catalogItemForm") CatalogItemForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                ServiceCatalogItem saved = catalogService.create(form);
                redirect.addFlashAttribute("success", "Service \"" + saved.getName() + "\" was added.");
                return "redirect:/catalog";
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        return formView(model, null);
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editForm(@PathVariable Long id, Model model) {
        ServiceCatalogItem item = catalogService.get(id);
        model.addAttribute("catalogItemForm", CatalogItemForm.from(item));
        return formView(model, item);
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("catalogItemForm") CatalogItemForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                ServiceCatalogItem saved = catalogService.update(id, form);
                redirect.addFlashAttribute("success", "Service \"" + saved.getName() + "\" was updated.");
                return "redirect:/catalog";
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        return formView(model, catalogService.get(id));
    }

    @PostMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public String setActive(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes redirect) {
        ServiceCatalogItem item = catalogService.setActive(id, active);
        redirect.addFlashAttribute("success", "Service \"" + item.getName() + "\" is now "
                + (active ? "active." : "inactive and hidden from new bookings."));
        return "redirect:/catalog";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        String name = catalogService.get(id).getName();
        catalogService.delete(id);
        redirect.addFlashAttribute("success", "Service \"" + name + "\" was deleted.");
        return "redirect:/catalog";
    }

    private String formView(Model model, ServiceCatalogItem existing) {
        model.addAttribute("item", existing);
        return "catalog/form";
    }
}
