package com.aiquote.backend.knowledgebase;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the "Moje materiały" catalog — concrete materials/parts the company uses on jobs
 * (e.g. "rura PVC 50mm"), as individually addressable rows rather than the single JSON
 * blob services/cennik lives in (see PriceListItem's javadoc for why). Three ways a row
 * gets here: the owner typing it in directly (MANUAL, plain create), a cennik document
 * import (UPLOADED, see KnowledgeSourceProcessor), or the owner simply mentioning it while
 * chatting with AI in "Wiedza firmy" (CHAT, see OnboardingService) — the latter two go
 * through upsertFromAi so re-processing the same document or mentioning the same material
 * twice in conversation refines one row instead of piling up duplicates.
 */
@Service
@RequiredArgsConstructor
public class PriceListItemService {

    private final PriceListItemRepository repository;

    @Transactional(readOnly = true)
    public List<PriceListItemResponse> list(Long companyId) {
        return repository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(PriceListItemResponse::from)
                .toList();
    }

    @Transactional
    public PriceListItemResponse create(Long companyId, PriceListItemRequest request) {
        PriceListItem item = repository.save(new PriceListItem(
                companyId, request.name().trim(), blankToNull(request.category()), request.price(), blankToNull(request.unit()), PriceListItemSource.MANUAL));
        return PriceListItemResponse.from(item);
    }

    @Transactional
    public PriceListItemResponse update(Long companyId, Long id, PriceListItemRequest request) {
        PriceListItem item = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new PriceListItemNotFoundException(id));
        item.update(request.name().trim(), blankToNull(request.category()), request.price(), blankToNull(request.unit()));
        return PriceListItemResponse.from(item);
    }

    @Transactional
    public void delete(Long companyId, Long id) {
        PriceListItem item = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new PriceListItemNotFoundException(id));
        repository.delete(item);
    }

    /**
     * AI-sourced write (import or chat): matched by name, case-insensitively, within the
     * same company — a repeat mention refines the existing row (refreshFromAi never lets
     * a null overwrite a known value) instead of creating a duplicate. A genuinely new
     * name becomes a new row with the given source.
     */
    @Transactional
    public PriceListItemResponse upsertFromAi(Long companyId, String name, String category, Double price, String unit, PriceListItemSource source) {
        String trimmedName = name.trim();
        PriceListItem item = repository.findByCompanyIdAndNameIgnoreCase(companyId, trimmedName)
                .map(existing -> {
                    existing.refreshFromAi(blankToNull(category), price, blankToNull(unit));
                    return existing;
                })
                .orElseGet(() -> repository.save(new PriceListItem(companyId, trimmedName, blankToNull(category), price, blankToNull(unit), source)));
        return PriceListItemResponse.from(item);
    }

    /**
     * Parses the save_price_list_items tool's {"items": [...]} shape and upserts each
     * one — shared by OnboardingService (chat) and KnowledgeSourceProcessor (document
     * import) so the "how do we turn a tool call into rows" logic lives in exactly one
     * place. Entries missing a name are silently skipped rather than failing the whole
     * batch — the AI occasionally emits an empty placeholder entry.
     */
    @Transactional
    public List<PriceListItemResponse> upsertAllFromToolInput(Long companyId, JsonNode toolInput, PriceListItemSource source) {
        List<PriceListItemResponse> saved = new ArrayList<>();
        JsonNode itemsNode = toolInput.path("items");
        if (!itemsNode.isArray()) {
            return saved;
        }
        for (JsonNode itemNode : itemsNode) {
            String name = itemNode.path("name").asText(null);
            if (name == null || name.isBlank()) {
                continue;
            }
            String category = itemNode.hasNonNull("category") ? itemNode.path("category").asText() : null;
            Double price = itemNode.hasNonNull("price") ? itemNode.path("price").asDouble() : null;
            String unit = itemNode.hasNonNull("unit") ? itemNode.path("unit").asText() : null;
            saved.add(upsertFromAi(companyId, name, category, price, unit, source));
        }
        return saved;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
