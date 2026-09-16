package com.aiquote.backend.knowledgebase;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyPricingProfileRepository extends JpaRepository<CompanyPricingProfile, Long> {

    Optional<CompanyPricingProfile> findByCompanyId(Long companyId);
}
