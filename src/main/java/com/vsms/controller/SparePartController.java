package com.vsms.controller;

import com.vsms.dto.SparePartForm;
import com.vsms.entity.SparePart;
import com.vsms.exception.BusinessRuleException;
import com.vsms.service.SparePartService;
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

/** Spare-parts inventory. Everyone can look (mechanics need it); staff change it; admins delete. */
@Controller
@RequestMapping("/parts")
@PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR','MECHANIC')")
public class SparePartController {

    private final SparePartService partService;

    public SparePartController(SparePartService partService) {
        this.partService = partService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "false") boolean low,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", partService.search(q, low, page));
        model.addAttribute("q", q);
        model.addAttribute("low", low);
        model.addAttribute("lowStockCount", partService.countLowStock());
        return "parts/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
    public String newForm(Model model) {
        model.addAttribute("sparePartForm", new SparePartForm());
        return formView(model, null);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
    public String create(@Valid @ModelAttribute("sparePartForm") SparePartForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                SparePart saved = partService.create(form);
                redirect.addFlashAttribute("success", "Part \"" + saved.getName() + "\" was added to the inventory.");
                return "redirect:/parts";
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        return formView(model, null);
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
    public String editForm(@PathVariable Long id, Model model) {
        SparePart part = partService.get(id);
        model.addAttribute("sparePartForm", SparePartForm.from(part));
        return formView(model, part);
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("sparePartForm") SparePartForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                SparePart saved = partService.update(id, form);
                redirect.addFlashAttribute("success", "Part \"" + saved.getName() + "\" was updated.");
                return "redirect:/parts";
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        return formView(model, partService.get(id));
    }

    @PostMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
    public String addStock(@PathVariable Long id, @RequestParam int quantity, RedirectAttributes redirect) {
        SparePart part = partService.addStock(id, quantity);
        redirect.addFlashAttribute("success", quantity + " unit(s) added to \"" + part.getName()
                + "\". Stock is now " + part.getStockQuantity() + ".");
        return "redirect:/parts";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        String name = partService.get(id).getName();
        partService.delete(id);
        redirect.addFlashAttribute("success", "Part \"" + name + "\" was deleted.");
        return "redirect:/parts";
    }

    private String formView(Model model, SparePart existing) {
        model.addAttribute("part", existing);
        return "parts/form";
    }
}
