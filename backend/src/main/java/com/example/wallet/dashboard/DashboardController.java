package com.example.wallet.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.auth.access.CanHoldWallets;
import com.example.wallet.dashboard.DashboardDtos.DashboardSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/dashboard")
@CanHoldWallets
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/summary")
    @Operation(summary = "Totals per currency, this month's in/out, 30-day cash flow, last 8 transactions")
    public DashboardSummary summary(AuthUser caller) {
        return dashboard.summary(caller);
    }
}
