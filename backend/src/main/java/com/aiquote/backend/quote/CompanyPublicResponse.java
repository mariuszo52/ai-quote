package com.aiquote.backend.quote;

/** Deliberately only branding + readiness — never pricing profile, AI reasoning, or
 * any other private company data (see quote.QuoteSecurityTest for the structural guard
 * on this package's public-facing types). */
public record CompanyPublicResponse(String name, boolean ready, String displayName, String primaryColor, String welcomeText, boolean hasLogo) {
}
