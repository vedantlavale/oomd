package com.vsms.controller;

import com.vsms.dto.ChangePasswordForm;
import com.vsms.exception.BusinessRuleException;
import com.vsms.security.Actor;
import com.vsms.service.UserAccountService;
import com.vsms.util.FormErrors;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Lets every signed-in person change their own password. */
@Controller
@RequestMapping("/account")
@PreAuthorize("isAuthenticated()")
public class AccountController {

    private final UserAccountService userService;

    public AccountController(UserAccountService userService) {
        this.userService = userService;
    }

    @GetMapping("/password")
    public String form(Model model) {
        model.addAttribute("changePasswordForm", new ChangePasswordForm());
        return "account/password";
    }

    @PostMapping("/password")
    public String change(@Valid @ModelAttribute("changePasswordForm") ChangePasswordForm form, BindingResult result,
                         Actor actor, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                userService.changeOwnPassword(actor, form);
                redirect.addFlashAttribute("success", "Your password was changed.");
                return "redirect:/";
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        return "account/password";
    }
}
