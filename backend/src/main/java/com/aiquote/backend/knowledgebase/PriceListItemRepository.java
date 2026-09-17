package com.aiquote.backend.knowledgebase;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceListItemRepository extends JpaRepository<PriceListItem, Long> {

    List<PriceListItem> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<PriceListItem> findByIdAndCompanyId(Long id, Long companyId);

    Optional<PriceListItem> findByCompanyIdAndNameIgnoreCase(Long companyId, String name);
}
