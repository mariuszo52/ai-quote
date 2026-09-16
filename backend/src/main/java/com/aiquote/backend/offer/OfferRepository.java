package com.aiquote.backend.offer;

import com.aiquote.backend.lead.LeadStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    Optional<Offer> findByQuoteIdAndCompanyId(Long quoteId, Long companyId);

    Optional<Offer> findByPublicToken(String publicToken);

    boolean existsByQuoteId(Long quoteId);

    /** Etap 18 dashboard aggregates — all group-by-currency, since offers aren't
     * guaranteed to share one currency across a company (industry-agnostic pricing, no
     * currency assumption baked in). Each row is {currency, sum, count}. */
    long countByCompanyIdAndStatusAndSentAtBetween(Long companyId, OfferStatus status, Instant from, Instant to);

    @Query("""
            SELECT o.currency, SUM(o.total), COUNT(o) FROM Offer o
            WHERE o.companyId = :companyId AND o.status = com.aiquote.backend.offer.OfferStatus.SENT
            AND o.sentAt BETWEEN :from AND :to
            GROUP BY o.currency
            """)
    List<Object[]> sumSentOffersByCurrency(@Param("companyId") Long companyId, @Param("from") Instant from, @Param("to") Instant to);

    /** "wygrana oferta" = an offer whose lead ended up WON — scoped to leads created in
     * the window, same convention as QuoteRepository#countByStatusForLeadsCreatedBetween. */
    @Query("""
            SELECT o.currency, SUM(o.total), COUNT(o) FROM Offer o
            WHERE o.companyId = :companyId
            AND o.quoteId IN (
                SELECT q.id FROM Quote q WHERE q.leadId IN (
                    SELECT l.id FROM Lead l WHERE l.companyId = :companyId AND l.status = :leadStatus AND l.createdAt BETWEEN :from AND :to
                )
            )
            GROUP BY o.currency
            """)
    List<Object[]> sumOffersByCurrencyForLeadsWithStatus(
            @Param("companyId") Long companyId,
            @Param("leadStatus") LeadStatus leadStatus,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
