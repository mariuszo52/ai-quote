package com.aiquote.backend.company;

import com.aiquote.backend.file.FileSignature;
import com.aiquote.backend.file.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");
    private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final long MAX_LOGO_SIZE_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_LOGO_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CompanyRepository companyRepository;
    private final StorageService storageService;

    public Company createCompany(String name) {
        String slug = generateUniqueSlug(name);
        return companyRepository.save(new Company(name, slug, CompanyStatus.ONBOARDING));
    }

    public Company getById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Company not found: " + id));
    }

    public Company getBySlug(String slug) {
        return companyRepository.findBySlug(slug)
                .orElseThrow(() -> new CompanyNotFoundException(slug));
    }

    @Transactional
    public Company activate(Long id) {
        Company company = getById(id);
        company.activate();
        return company;
    }

    @Transactional
    public Company updateBranding(Long companyId, UpdateBrandingRequest request) {
        Company company = getById(companyId);
        company.updateBranding(
                request.displayName(), request.primaryColor(), request.welcomeText(),
                request.contactEmail(), request.contactPhone(), request.address());
        return company;
    }

    @Transactional
    public Company uploadLogo(Long companyId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidLogoException("Plik jest pusty.");
        }
        if (file.getSize() > MAX_LOGO_SIZE_BYTES) {
            throw new InvalidLogoException("Logo jest za duże (limit 2 MB).");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_LOGO_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidLogoException("Obsługiwane są tylko obrazy JPEG, PNG lub WebP.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidLogoException("Nie udało się odczytać pliku.");
        }
        // Etap 21: the declared Content-Type above is client-controlled and trivially
        // spoofable — confirm the bytes actually are the image format claimed.
        if (!FileSignature.isImage(bytes, contentType)) {
            throw new InvalidLogoException("Plik nie jest prawidłowym obrazem JPEG, PNG ani WebP.");
        }

        Company company = getById(companyId);
        String oldStorageKey = company.getLogoStorageKey();

        String newStorageKey = storageService.store(
                companyId, file.getOriginalFilename(), contentType, new ByteArrayInputStream(bytes), bytes.length);

        company.attachLogo(newStorageKey, contentType);

        if (oldStorageKey != null) {
            storageService.delete(oldStorageKey);
        }
        return company;
    }

    @Transactional
    public void removeLogo(Long companyId) {
        Company company = getById(companyId);
        String storageKey = company.getLogoStorageKey();
        company.removeLogo();
        if (storageKey != null) {
            storageService.delete(storageKey);
        }
    }

    public LogoContent getLogo(Long companyId) {
        return readLogo(getById(companyId));
    }

    public LogoContent getPublicLogo(String slug) {
        return readLogo(getBySlug(slug));
    }

    private LogoContent readLogo(Company company) {
        if (!company.hasLogo()) {
            throw new LogoNotFoundException();
        }
        try (InputStream in = storageService.retrieve(company.getLogoStorageKey())) {
            return new LogoContent(in.readAllBytes(), company.getLogoContentType());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stored logo for company " + company.getId(), e);
        }
    }

    private String generateUniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        while (companyRepository.existsBySlug(candidate)) {
            candidate = base + "-" + randomSuffix();
        }
        return candidate;
    }

    private String slugify(String name) {
        String lower = name.toLowerCase(Locale.ROOT).trim();
        String replaced = NON_ALNUM.matcher(lower).replaceAll("-");
        String trimmed = EDGE_DASHES.matcher(replaced).replaceAll("");
        return trimmed.isEmpty() ? "firma" : trimmed;
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(SUFFIX_ALPHABET.charAt(RANDOM.nextInt(SUFFIX_ALPHABET.length())));
        }
        return sb.toString();
    }
}
