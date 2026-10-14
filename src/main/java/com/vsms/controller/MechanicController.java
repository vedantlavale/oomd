package com.vsms.controller;

import com.vsms.dto.MechanicForm;
import com.vsms.entity.Mechanic;
import com.vsms.service.MechanicService;
import com.vsms.util.Filters;
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

/** Everyone on the staff can look at mechanics; only administrators change them. */
@Controller
@RequestMapping("/mechanics")
@PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
public class MechanicController {

    private final MechanicService mechanicService;

    public MechanicController(MechanicService mechanicService) {
        this.mechanicService = mechanicService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", mechanicService.search(q, Filters.active(status), page));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        return "mechanics/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newForm(Model model) {
        model.addAttribute("mechanicForm", new MechanicForm());
        return formView(model, null);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String create(@Valid @ModelAttribute("mechanicForm") MechanicForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return formView(model, null);
        }
        Mechanic saved = mechanicService.create(form);
        redirect.addFlashAttribute("success", "Mechanic " + saved.getName() + " was added.");
        return "redirect:/mechanics/" + saved.getId();
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("details", mechanicService.details(id));
        return "mechanics/view";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editForm(@PathVariable Long id, Model model) {
        Mechanic mechanic = mechanicService.get(id);
        model.addAttribute("mechanicForm", MechanicForm.from(mechanic));
        return formView(model, mechanic);
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("mechanicForm") MechanicForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return formView(model, mechanicService.get(id));
        }
        Mechanic saved = mechanicService.update(id, form);
        redirect.addFlashAttribute("success", "Mechanic " + saved.getName() + " was updated.");
        return "redirect:/mechanics/" + id;
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
        Mechanic mechanic = mechanicService.deactivate(id);
        redirect.addFlashAttribute("success", mechanic.getName() + " was deactivated and can no longer be assigned to jobs.");
        return "redirect:/mechanics/" + id;
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public String activate(@PathVariable Long id, RedirectAttributes redirect) {
        Mechanic mechanic = mechanicService.activate(id);
        redirect.addFlashAttribute("success", mechanic.getName() + " is active again.");
        return "redirect:/mechanics/" + id;
    }

    private String formView(Model model, Mechanic existing) {
        model.addAttribute("mechanic", existing);
        return "mechanics/form";
    }
}
