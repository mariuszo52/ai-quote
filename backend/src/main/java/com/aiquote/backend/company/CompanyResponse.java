package com.aiquote.backend.company;

public record CompanyResponse(
        Long id,
        String name,
        String slug,
        CompanyStatus status,
        String displayName,
        boolean hasLogo,
        String primaryColor,
        String welcomeText,
        String contactEmail,
        String contactPhone,
        String address) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getSlug(),
                company.getStatus(),
                company.getDisplayName(),
                company.hasLogo(),
                company.getResolvedPrimaryColor(),
                company.getResolvedWelcomeText(),
                company.getContactEmail(),
                company.getContactPhone(),
                company.getAddress());
    }
}
