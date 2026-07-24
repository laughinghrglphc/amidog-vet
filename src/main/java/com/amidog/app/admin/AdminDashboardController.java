package com.amidog.app.admin;

import com.amidog.app.auth.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.amidog.app.admin.AdminDtos.DashboardResponse;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public final class AdminDashboardController {

    private final AdminDashboardService dashboard;

    public AdminDashboardController(
            AdminDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    DashboardResponse dashboard(
            @AuthenticationPrincipal AccountPrincipal principal) {
        return dashboard.dashboard(principal);
    }
}
