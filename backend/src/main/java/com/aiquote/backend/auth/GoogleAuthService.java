package com.aiquote.backend.auth;

import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.tenant.UserRole;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.security.GeneralSecurityException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Sign in with Google" (Google Identity Services) — the frontend gets a signed ID
 * token straight from Google and hands it to us; we verify it here (signature,
 * audience, expiry all checked by GoogleIdTokenVerifier, built as a bean in
 * GoogleAuthConfig) and issue our own JWT exactly like AuthService.login/register
 * already do. No client secret or server-side redirect/callback needed for this flow.
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;
    private final CompanyService companyService;
    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse authenticate(String idTokenString) {
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            throw new InvalidGoogleTokenException();
        }
        if (idToken == null) {
            throw new InvalidGoogleTokenException();
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String googleId = payload.getSubject();

        AppUser user = appUserRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = createNewAccount(email, googleId, displayNameOf(payload));
        } else if (user.getGoogleId() == null) {
            // Existing local (password) account signing in with Google for the first
            // time — Google has already verified this email belongs to them, so this
            // is a safe, low-friction link rather than a dead-end error. The password
            // keeps working too.
            user.linkGoogleId(googleId);
        }

        Company company = companyService.getById(user.getCompanyId());
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, company.getId(), company.getSlug());
    }

    private AppUser createNewAccount(String email, String googleId, String displayName) {
        Company company = companyService.createCompany(defaultCompanyName(displayName, email));
        try {
            return appUserRepository.save(AppUser.google(company.getId(), email, googleId, UserRole.OWNER));
        } catch (DataIntegrityViolationException e) {
            // Same race-window guard as AuthService.register (Etap 21): two concurrent
            // first-time Google sign-ins for the same email can't both win the
            // unique-email insert. The DB is the real guard; recover by re-reading.
            return appUserRepository.findByEmail(email).orElseThrow(() -> e);
        }
    }

    private String defaultCompanyName(String displayName, String email) {
        if (displayName != null && !displayName.isBlank()) {
            return "Firma " + displayName;
        }
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        return "Firma " + localPart;
    }

    private String displayNameOf(GoogleIdToken.Payload payload) {
        Object name = payload.get("name");
        return name instanceof String ? (String) name : null;
    }
}
