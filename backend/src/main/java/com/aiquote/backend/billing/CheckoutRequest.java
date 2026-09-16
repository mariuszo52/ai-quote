package com.aiquote.backend.billing;

import jakarta.validation.constraints.NotNull;
import com.aiquote.backend.company.CompanyPlan;

public record CheckoutRequest(@NotNull CompanyPlan plan) {
}
