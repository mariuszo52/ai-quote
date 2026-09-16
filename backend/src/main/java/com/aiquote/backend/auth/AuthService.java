package com.aiquote.backend.auth;

import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.tenant.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CompanyService companyService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException(request.email());
        }

        Company company = companyService.createCompany(request.companyName());
        AppUser user;
        try {
            user = appUserRepository.save(AppUser.local(
                    company.getId(),
                    request.email(),
                    passwordEncoder.encode(request.password()),
                    UserRole.OWNER));
        } catch (DataIntegrityViolationException e) {
            // Etap 21: the pre-check above has a race window (two concurrent
            // registrations with the same email can both pass it) — app_users.email's
            // DB-level unique constraint is the real guard. Translate that into the
            // same clean, expected error instead of letting it surface as a raw 500.
            throw new EmailAlreadyInUseException(request.email());
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, company.getId(), company.getSlug());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        // Must be checked before matches() — BCryptPasswordEncoder throws
        // IllegalArgumentException on a null encoded password rather than just
        // returning false, and a Google-only account has no password hash at all.
        if (user.getPasswordHash() == null) {
            throw new GoogleOnlyAccountException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        Company company = companyService.getById(user.getCompanyId());
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, company.getId(), company.getSlug());
    }
}
