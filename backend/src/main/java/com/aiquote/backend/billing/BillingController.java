package com.aiquote.backend.billing;

import com.aiquote.backend.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-facing (authenticated, tenant-scoped) plan/subscription endpoints. The
 * unauthenticated Stripe webhook lives on StripeWebhookController — kept separate so
 * the permitAll security rule is scoped to exactly one path, not this whole controller. */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/status")
    public BillingStatusResponse status() {
        return billingService.getStatus(TenantContext.currentCompanyId());
    }

    @PostMapping("/checkout")
    public RedirectUrlResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return billingService.createCheckoutSession(TenantContext.currentCompanyId(), request.plan());
    }

    @PostMapping("/portal")
    public RedirectUrlResponse portal() {
        return billingService.createPortalSession(TenantContext.currentCompanyId());
    }
}
