package com.aiquote.backend.knowledgebase;

import com.aiquote.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * How one company prices its jobs, as structured data (see PricingProfileData) stored
 * in profileJson — no fixed DB schema per field so the same entity works for any
 * service industry. manuallyEditedAt is set only when the owner explicitly saves the
 * edit form; it's what tells AI-driven updates (onboarding chat, document import) to
 * merge instead of blindly replacing — see PricingProfileMerger.
 */
@Entity
@Table(name = "company_pricing_profiles")
@Getter
@NoArgsConstructor
public class CompanyPricingProfile extends BaseEntity {

    @Column(name = "company_id", nullable = false, unique = true, updatable = false)
    private Long companyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_json", nullable = false, columnDefinition = "jsonb")
    private String profileJson;

    @Column(name = "manually_edited_at")
    private Instant manuallyEditedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CompanyPricingProfile(Long companyId) {
        this.companyId = companyId;
        this.profileJson = "{}";
        this.updatedAt = Instant.now();
    }

    public void updateFromAi(String profileJson) {
        this.profileJson = profileJson;
        this.updatedAt = Instant.now();
    }

    public void updateManually(String profileJson) {
        this.profileJson = profileJson;
        this.updatedAt = Instant.now();
        this.manuallyEditedAt = this.updatedAt;
    }
}
