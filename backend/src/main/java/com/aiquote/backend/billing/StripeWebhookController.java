package com.aiquote.backend.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one unauthenticated endpoint in the billing surface — Stripe calls this directly,
 * so it can't require a JWT. Signature verification (see StripeGateway) IS the entire
 * security boundary here, same rigor as every other /api/public/** endpoint audited in
 * the Etap 21 security review. Kept as its own controller (not folded into
 * BillingController) so SecurityConfig's permitAll rule is scoped to exactly this path.
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final BillingService billingService;

    @PostMapping("/webhook")
    public void webhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
        billingService.handleWebhookEvent(payload, signature);
    }
}
