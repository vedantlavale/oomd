package com.vsms.controller;

import com.vsms.dto.PasswordResetForm;
import com.vsms.dto.UserCreateForm;
import com.vsms.dto.UserEditForm;
import com.vsms.entity.UserAccount;
import com.vsms.enums.Role;
import com.vsms.exception.BusinessRuleException;
import com.vsms.security.Actor;
import com.vsms.service.MechanicService;
import com.vsms.service.UserAccountService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Administrator-only management of login accounts. */
@Controller
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserAccountService userService;
    private final MechanicService mechanicService;

    public UserController(UserAccountService userService, MechanicService mechanicService) {
        this.userService = userService;
        this.mechanicService = mechanicService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "users/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("userCreateForm", new UserCreateForm());
        return createView(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("userCreateForm") UserCreateForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                UserAccount saved = userService.create(form);
                redirect.addFlashAttribute("success", "Account \"" + saved.getUsername() + "\" was created.");
                return "redirect:/users";
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        return createView(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        UserAccount user = userService.get(id);
        model.addAttribute("userEditForm", UserEditForm.from(user));
        return editView(model, user);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("userEditForm") UserEditForm form,
                         BindingResult result, Actor actor, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                UserAccount saved = userService.update(id, form, actor);
                redirect.addFlashAttribute("success", "Account \"" + saved.getUsername() + "\" was updated.");
                return "redirect:/users";
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        return editView(model, userService.get(id));
    }

    @GetMapping("/{id}/password")
    public String passwordForm(@PathVariable Long id, Model model) {
        model.addAttribute("passwordResetForm", new PasswordResetForm());
        model.addAttribute("user", userService.get(id));
        return "users/password";
    }

    @PostMapping("/{id}/password")
    public String resetPassword(@PathVariable Long id, @Valid @ModelAttribute("passwordResetForm") PasswordResetForm form,
                                BindingResult result, Model model, RedirectAttributes redirect) {
        UserAccount user = userService.get(id);
        if (!result.hasErrors()) {
            try {
                userService.resetPassword(id, form);
                redirect.addFlashAttribute("success", "The password of \"" + user.getUsername() + "\" was changed.");
                return "redirect:/users";
            } catch (BusinessRuleException ex) {
                FormErrors.apply(result, ex);
            }
        }
        model.addAttribute("user", user);
        return "users/password";
    }

    private String createView(Model model) {
        model.addAttribute("roles", Role.values());
        model.addAttribute("mechanics", mechanicService.findActive());
        return "users/new";
    }

    private String editView(Model model, UserAccount user) {
        model.addAttribute("user", user);
        model.addAttribute("roles", Role.values());
        model.addAttribute("mechanics", mechanicService.findAll());
        return "users/edit";
    }
}
