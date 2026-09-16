package com.aiquote.backend.knowledgebase;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, Long> {

    List<KnowledgeSource> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}
