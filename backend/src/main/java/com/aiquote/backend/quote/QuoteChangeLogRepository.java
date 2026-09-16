package com.aiquote.backend.quote;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteChangeLogRepository extends JpaRepository<QuoteChangeLogEntry, Long> {

    List<QuoteChangeLogEntry> findByQuoteIdOrderByCreatedAtAsc(Long quoteId);
}
