package com.aiquote.backend.quote;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    List<Quote> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<Quote> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Quote> findByLeadIdAndCompanyId(Long leadId, Long companyId);

    /** Etap 18 dashboard aggregates. */
    long countByCompanyIdAndCreatedAtBetween(Long companyId, Instant from, Instant to);

    /** Current pending-approval queue — deliberately NOT date-ranged (see
     * DashboardService's javadoc): it's "how many need attention right now", not tied
     * to when they were created. */
    long countByCompanyIdAndStatusIn(Long companyId, Collection<QuoteStatus> statuses);

    /** "lead -> sent quote" conversion numerator: quotes that reached the given status,
     * scoped to leads created in the window — a subquery rather than a real @ManyToOne
     * join, same reasoning as everywhere else Quote reaches Lead only via leadId. */
    @Query("""
            SELECT COUNT(q) FROM Quote q
            WHERE q.companyId = :companyId AND q.status = :status
            AND q.leadId IN (SELECT l.id FROM Lead l WHERE l.companyId = :companyId AND l.createdAt BETWEEN :from AND :to)
            """)
    long countByStatusForLeadsCreatedBetween(
            @Param("companyId") Long companyId,
            @Param("status") QuoteStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
