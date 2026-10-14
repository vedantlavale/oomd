package com.vsms.controller;

import com.vsms.security.Actor;
import com.vsms.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Home page. Staff see the workshop overview; mechanics see their own work. */
@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/")
    public String dashboard(Actor actor, Model model) {
        if (actor.isMechanic()) {
            model.addAttribute("mine", dashboardService.mechanic(actor.mechanicId()));
        } else {
            model.addAttribute("stats", dashboardService.staff());
        }
        return "dashboard";
    }
}
