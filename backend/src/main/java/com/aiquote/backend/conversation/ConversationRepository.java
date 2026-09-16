package com.aiquote.backend.conversation;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findFirstByCompanyIdAndTypeAndStatusOrderByCreatedAtDesc(
            Long companyId, ConversationType type, ConversationStatus status);

    Optional<Conversation> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Conversation> findByIdAndPublicToken(Long id, String publicToken);

    /** "wszystkie zapytania" (Etap 18) — every client-quote conversation started, whether
     * or not it ever became a Lead; that's exactly what makes it distinct from
     * countByCompanyIdAndCreatedAtBetween on LeadRepository, used for the inquiry->lead
     * conversion rate. */
    long countByCompanyIdAndTypeAndCreatedAtBetween(Long companyId, ConversationType type, Instant from, Instant to);
}
