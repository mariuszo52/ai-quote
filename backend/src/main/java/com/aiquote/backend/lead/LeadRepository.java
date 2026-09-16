package com.aiquote.backend.lead;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<Lead> findByIdAndCompanyId(Long id, Long companyId);

    /** Etap 20: lets submitContact recover the winning row when a raced double-submit
     * hits the DB's unique constraint on conversation_id instead of erroring. */
    Optional<Lead> findByConversationId(Long conversationId);

    /** Etap 18 dashboard aggregates — every count is DB-side (COUNT query), never a
     * findAll().size() over hydrated entities. */
    long countByCompanyIdAndCreatedAtBetween(Long companyId, Instant from, Instant to);

    long countByCompanyIdAndStatusAndCreatedAtBetween(Long companyId, LeadStatus status, Instant from, Instant to);
}
