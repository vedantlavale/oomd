package com.vsms.controller;

import com.vsms.dto.PaymentForm;
import com.vsms.entity.Invoice;
import com.vsms.entity.Payment;
import com.vsms.enums.PaymentMethod;
import com.vsms.enums.PaymentRecordStatus;
import com.vsms.enums.PaymentStatus;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.security.Actor;
import com.vsms.service.InvoiceService;
import com.vsms.service.PaymentService;
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
@RequestMapping("/payments")
@PreAuthorize("hasAnyRole('ADMIN','SERVICE_ADVISOR')")
public class PaymentController {

    private final PaymentService paymentService;
    private final InvoiceService invoiceService;

    public PaymentController(PaymentService paymentService, InvoiceService invoiceService) {
        this.paymentService = paymentService;
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) PaymentMethod method,
                       @RequestParam(required = false) PaymentRecordStatus status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", paymentService.search(q, status, method, from, to, page));
        model.addAttribute("q", q);
        model.addAttribute("method", method);
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("recordStatuses", PaymentRecordStatus.values());
        return "payments/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam Long invoiceId, Model model) {
        Invoice invoice = invoiceService.get(invoiceId);
        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessRuleException("Invoice " + invoice.getInvoiceNumber() + " is already fully paid.");
        }
        PaymentForm form = new PaymentForm();
        form.setInvoiceId(invoiceId);
        form.setAmount(invoice.getBalanceDue());
        model.addAttribute("paymentForm", form);
        return formView(model, invoice);
    }

    @PostMapping
    public String record(@Valid @ModelAttribute("paymentForm") PaymentForm form, BindingResult result,
                         Actor actor, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                Payment payment = paymentService.record(form, actor);
                redirect.addFlashAttribute("success", "Payment " + payment.getReceiptNumber() + " recorded. Invoice is now "
                        + payment.getInvoice().getPaymentStatus().getLabel().toLowerCase() + ".");
                return "redirect:/invoices/" + payment.getInvoice().getId();
            } catch (BusinessRuleException ex) {
                if (ex.getField() == null || "invoiceId".equals(ex.getField())) {
                    throw ex;
                }
                FormErrors.apply(result, ex);
            }
        }
        if (form.getInvoiceId() == null) {
            throw new ResourceNotFoundException("Invoice", null);
        }
        return formView(model, invoiceService.get(form.getInvoiceId()));
    }

    @GetMapping("/{id}")
    public String receipt(@PathVariable Long id, Model model) {
        model.addAttribute("payment", paymentService.get(id));
        return "payments/receipt";
    }

    @GetMapping("/{id}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    public String reverseForm(@PathVariable Long id, Model model) {
        model.addAttribute("payment", paymentService.get(id));
        return "payments/reverse";
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    public String reverse(@PathVariable Long id, @RequestParam(required = false) String reason, Actor actor,
                          Model model, RedirectAttributes redirect) {
        try {
            Payment payment = paymentService.reverse(id, reason, actor);
            redirect.addFlashAttribute("success", "Payment " + payment.getReceiptNumber() + " was reversed.");
            return "redirect:/invoices/" + payment.getInvoice().getId();
        } catch (BusinessRuleException ex) {
            model.addAttribute("payment", paymentService.get(id));
            model.addAttribute("reason", reason);
            model.addAttribute("error", ex.getMessage());
            return "payments/reverse";
        }
    }

    private String formView(Model model, Invoice invoice) {
        model.addAttribute("invoice", invoice);
        model.addAttribute("methods", PaymentMethod.values());
        return "payments/form";
    }
}
