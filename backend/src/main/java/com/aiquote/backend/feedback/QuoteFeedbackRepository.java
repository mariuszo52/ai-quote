package com.aiquote.backend.feedback;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuoteFeedbackRepository extends JpaRepository<QuoteFeedback, Long> {

    Optional<QuoteFeedback> findByQuoteIdAndCompanyId(Long quoteId, Long companyId);

    boolean existsByQuoteId(Long quoteId);

    /** Etap 18 AI-accuracy dashboard stats. Null AVG (no rows, or all diffPercentage
     * null e.g. every AI total was 0) is exactly the "not enough data" signal
     * DashboardService needs — never coerced to 0. */
    long countByCompanyIdAndCreatedAtBetween(Long companyId, Instant from, Instant to);

    long countByCompanyIdAndDiffAmountNotAndCreatedAtBetween(Long companyId, double diffAmount, Instant from, Instant to);

    @Query("SELECT AVG(f.diffPercentage) FROM QuoteFeedback f WHERE f.companyId = :companyId AND f.createdAt BETWEEN :from AND :to AND f.diffPercentage IS NOT NULL")
    Double averageDiffPercentage(@Param("companyId") Long companyId, @Param("from") Instant from, @Param("to") Instant to);
}
