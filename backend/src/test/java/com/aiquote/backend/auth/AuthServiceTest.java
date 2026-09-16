package com.aiquote.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.company.CompanyStatus;
import com.aiquote.backend.tenant.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Covers the Google sign-in feature's effect on normal login: an account created via
 * Google has no password hash, and attempting a normal login for it must return a
 * clear "use Google" message rather than crashing on a null-password comparison or
 * showing the generic invalid-credentials message. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CompanyService companyService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(companyService, appUserRepository, passwordEncoder, jwtService);
    }

    private Company company(long id) {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "id", id);
        return company;
    }

    @Test
    void loginRejectsAGoogleOnlyAccountWithAClearMessageInsteadOfCrashing() {
        AppUser user = AppUser.google(5L, "owner@example.com", "google-sub-123", UserRole.OWNER);
        when(appUserRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("owner@example.com", "anyPassword123")))
                .isInstanceOf(GoogleOnlyAccountException.class);
    }

    @Test
    void loginStillWorksNormallyForALocalAccount() {
        AppUser user = AppUser.local(5L, "owner@example.com", "hashed", UserRole.OWNER);
        when(appUserRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", "hashed")).thenReturn(true);
        when(companyService.getById(5L)).thenReturn(company(5L));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = service.login(new LoginRequest("owner@example.com", "correctPassword"));

        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    void loginRejectsAWrongPasswordForALocalAccount() {
        AppUser user = AppUser.local(5L, "owner@example.com", "hashed", UserRole.OWNER);
        when(appUserRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("owner@example.com", "wrongPassword")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsAnUnknownEmail() {
        when(appUserRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "whatever123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
