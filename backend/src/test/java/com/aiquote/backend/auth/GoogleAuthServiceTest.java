package com.aiquote.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.company.CompanyStatus;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import java.security.GeneralSecurityException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Covers all three account paths for "Sign in with Google": brand-new user, linking
 * an existing local (password) account, and a returning Google user — plus the
 * invalid-token rejection. GoogleIdTokenVerifier is mocked since it talks to Google's
 * real public-key endpoint; GoogleIdToken.Payload is real (it's a plain POJO with
 * setters) rather than mocked, which is simpler and just as accurate. */
@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private GoogleIdTokenVerifier verifier;
    @Mock
    private CompanyService companyService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private JwtService jwtService;

    private GoogleAuthService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAuthService(verifier, companyService, appUserRepository, jwtService);
    }

    private Company company(long id) {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "id", id);
        return company;
    }

    /** A real GoogleIdToken (not a mock) — getPayload() resolves to a final method
     * somewhere in this class's hierarchy that Mockito can't stub, and there's a
     * perfectly usable real constructor anyway (we're not re-verifying its signature,
     * verifier.verify(...) is what's stubbed to hand this object back). */
    private GoogleIdToken tokenFor(String email, String subject, String name) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload().setEmail(email).setSubject(subject);
        if (name != null) {
            payload.set("name", name);
        }
        return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
    }

    @Test
    void invalidTokenIsRejectedCleanly() throws Exception {
        when(verifier.verify("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> service.authenticate("bad-token")).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void aVerificationExceptionIsAlsoRejectedCleanly() throws Exception {
        when(verifier.verify("bad-token")).thenThrow(new GeneralSecurityException("boom"));

        assertThatThrownBy(() -> service.authenticate("bad-token")).isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void aBrandNewEmailCreatesACompanyAndAGoogleOnlyAccount() throws Exception {
        when(verifier.verify("token")).thenReturn(tokenFor("nowy@example.com", "google-sub-1", "Jan Kowalski"));
        when(appUserRepository.findByEmail("nowy@example.com")).thenReturn(Optional.empty());
        Company newCompany = company(9L);
        when(companyService.createCompany(anyString())).thenReturn(newCompany);
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyService.getById(9L)).thenReturn(newCompany);
        when(jwtService.generateToken(any())).thenReturn("jwt-token");

        AuthResponse response = service.authenticate("token");

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.companyId()).isEqualTo(9L);

        ArgumentCaptor<AppUser> savedUser = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getEmail()).isEqualTo("nowy@example.com");
        assertThat(savedUser.getValue().getGoogleId()).isEqualTo("google-sub-1");
        assertThat(savedUser.getValue().getPasswordHash()).isNull();
    }

    @Test
    void anExistingLocalAccountGetsItsGoogleIdLinkedRatherThanRejected() throws Exception {
        AppUser existing = AppUser.local(5L, "owner@example.com", "hashed-password", com.aiquote.backend.tenant.UserRole.OWNER);
        when(verifier.verify("token")).thenReturn(tokenFor("owner@example.com", "google-sub-2", "Anna Nowak"));
        when(appUserRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(existing));
        when(companyService.getById(5L)).thenReturn(company(5L));
        when(jwtService.generateToken(existing)).thenReturn("jwt-token");

        service.authenticate("token");

        assertThat(existing.getGoogleId()).isEqualTo("google-sub-2");
        assertThat(existing.getPasswordHash()).isEqualTo("hashed-password"); // still works too
        verify(companyService, never()).createCompany(any());
    }

    @Test
    void aReturningGoogleUserJustLogsInWithoutAnyMutation() throws Exception {
        AppUser existing = AppUser.google(5L, "owner@example.com", "google-sub-3", com.aiquote.backend.tenant.UserRole.OWNER);
        when(verifier.verify("token")).thenReturn(tokenFor("owner@example.com", "google-sub-3", "Anna Nowak"));
        when(appUserRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(existing));
        when(companyService.getById(5L)).thenReturn(company(5L));
        when(jwtService.generateToken(existing)).thenReturn("jwt-token");

        AuthResponse response = service.authenticate("token");

        assertThat(response.token()).isEqualTo("jwt-token");
        verify(appUserRepository, never()).save(any());
    }
}
