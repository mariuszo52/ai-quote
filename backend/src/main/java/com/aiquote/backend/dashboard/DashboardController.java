package com.aiquote.backend.dashboard;

import com.aiquote.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Owner-facing (authenticated, tenant-scoped) dashboard/statistics (Etap 18). */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardResponse get(@RequestParam(required = false) String range) {
        return dashboardService.getDashboard(TenantContext.currentCompanyId(), DashboardRange.parse(range));
    }
}
