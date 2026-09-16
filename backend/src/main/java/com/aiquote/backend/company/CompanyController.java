package com.aiquote.backend.company;

import com.aiquote.backend.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Owner-facing (authenticated, tenant-scoped) company settings, including branding
 * (Etap 17). Every method here reaches only the caller's own company —
 * TenantContext.currentCompanyId() is derived from the JWT, so there is no request
 * parameter through which one company could ever address another's data. */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping("/me")
    public CompanyResponse me() {
        Company company = companyService.getById(TenantContext.currentCompanyId());
        return CompanyResponse.from(company);
    }

    @PutMapping("/branding")
    public CompanyResponse updateBranding(@Valid @RequestBody UpdateBrandingRequest request) {
        Company company = companyService.updateBranding(TenantContext.currentCompanyId(), request);
        return CompanyResponse.from(company);
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompanyResponse uploadLogo(@RequestParam("file") MultipartFile file) {
        Company company = companyService.uploadLogo(TenantContext.currentCompanyId(), file);
        return CompanyResponse.from(company);
    }

    @DeleteMapping("/logo")
    public CompanyResponse removeLogo() {
        companyService.removeLogo(TenantContext.currentCompanyId());
        return CompanyResponse.from(companyService.getById(TenantContext.currentCompanyId()));
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        LogoContent logo = companyService.getLogo(TenantContext.currentCompanyId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(logo.bytes());
    }
}
