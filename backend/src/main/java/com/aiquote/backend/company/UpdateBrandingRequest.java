package com.aiquote.backend.company;

import jakarta.validation.constraints.Pattern;

/** All fields optional — a company can set as much or as little branding as it wants;
 * anything left null falls back to a sensible default (see Company#getResolved*). */
public record UpdateBrandingRequest(
        String displayName,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Kolor musi być w formacie hex, np. #4f46e5") String primaryColor,
        String welcomeText,
        String contactEmail,
        String contactPhone,
        String address) {
}
