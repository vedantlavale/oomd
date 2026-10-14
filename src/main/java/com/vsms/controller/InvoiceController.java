package com.vsms.controller;

import com.vsms.dto.InvoiceForm;
import com.vsms.entity.Invoice;
import com.vsms.enums.PaymentStatus;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.security.Actor;
import com.vsms.service.InvoiceService;
import com.vsms.util.FormErrors;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/invoices")
@PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) PaymentStatus status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", invoiceService.search(q, status, from, to, page));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("statuses", PaymentStatus.values());
        return "invoices/list";
    }

    /** Shows what the invoice for a completed job will contain and asks for the tax rate. */
    @GetMapping("/new")
    public String newForm(@RequestParam Long jobId, Model model) {
        Invoice existing = invoiceService.findByJob(jobId);
        if (existing != null) {
            throw new BusinessRuleException("Invoice " + existing.getInvoiceNumber() + " has already been generated for this job.");
        }
        var preview = invoiceService.preview(jobId);
        InvoiceForm form = new InvoiceForm();
        form.setJobId(jobId);
        form.setTaxPercent(preview.defaultTaxPercent());
        model.addAttribute("invoiceForm", form);
        model.addAttribute("preview", preview);
        return "invoices/new";
    }

    @PostMapping
    public String generate(@Valid @ModelAttribute("invoiceForm") InvoiceForm form, BindingResult result,
                           Actor actor, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                Invoice invoice = invoiceService.generate(form.getJobId(), form.getTaxPercent(), actor);
                redirect.addFlashAttribute("success", "Invoice " + invoice.getInvoiceNumber() + " was generated.");
                return "redirect:/invoices/" + invoice.getId();
            } catch (BusinessRuleException ex) {
                if (ex.getField() == null) {
                    throw ex;
                }
                FormErrors.apply(result, ex);
            }
        }
        if (form.getJobId() == null) {
            throw new ResourceNotFoundException("Job card", null);
        }
        model.addAttribute("preview", invoiceService.preview(form.getJobId()));
        return "invoices/new";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("invoice", invoiceService.get(id));
        return "invoices/view";
    }
}
