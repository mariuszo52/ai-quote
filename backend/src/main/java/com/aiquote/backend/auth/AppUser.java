package com.aiquote.backend.auth;

import com.aiquote.backend.common.BaseEntity;
import com.aiquote.backend.tenant.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_users")
@Getter
@NoArgsConstructor
public class AppUser extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false, unique = true)
    private String email;

    /** Null for a Google-only account (see {@link #google}) — AuthService.login must
     * check this before attempting a password match, not just let it fail. */
    @Column(name = "password_hash")
    private String passwordHash;

    /** Google's stable per-user subject ID. Null for a local (password-only) account
     * that has never signed in with Google. Both fields can be non-null at once — see
     * GoogleAuthService's account-linking path. */
    @Column(name = "google_id", unique = true)
    private String googleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    private AppUser(Long companyId, String email, String passwordHash, String googleId, UserRole role) {
        this.companyId = companyId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.googleId = googleId;
        this.role = role;
    }

    public static AppUser local(Long companyId, String email, String passwordHash, UserRole role) {
        return new AppUser(companyId, email, passwordHash, null, role);
    }

    public static AppUser google(Long companyId, String email, String googleId, UserRole role) {
        return new AppUser(companyId, email, null, googleId, role);
    }

    /** Links a Google identity to an existing local account (see GoogleAuthService). */
    public void linkGoogleId(String googleId) {
        this.googleId = googleId;
    }
}
