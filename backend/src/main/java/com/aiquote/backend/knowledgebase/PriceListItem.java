package com.aiquote.backend.knowledgebase;

import com.aiquote.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single material/part the company uses on jobs (e.g. "rura PVC 50mm", "klimatyzator
 * Daikin X 3,5kW") — distinct from PricingServiceEntry, which describes priced *services*
 * (labor). Stored as individual rows (unlike the services list, which lives as one JSON
 * blob per company) specifically so the owner can edit/delete a single item by id from
 * "Moje materiały" instead of resubmitting the whole list.
 */
@Entity
@Table(name = "price_list_items")
@Getter
@NoArgsConstructor
public class PriceListItem extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    private String category;

    private Double price;

    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PriceListItemSource source;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PriceListItem(Long companyId, String name, String category, Double price, String unit, PriceListItemSource source) {
        this.companyId = companyId;
        this.name = name;
        this.category = category;
        this.price = price;
        this.unit = unit;
        this.source = source;
        this.updatedAt = Instant.now();
    }

    /** Owner-initiated edit via "Moje materiały" — source is deliberately left untouched:
     * editing a value doesn't change where the item originally came from. */
    public void update(String name, String category, Double price, String unit) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.unit = unit;
        this.updatedAt = Instant.now();
    }

    /** Called when a later import/chat mention of the same material (matched by name)
     * refines what's already known — same "AI shouldn't silently overwrite with less"
     * spirit as PricingProfileMerger, but simpler since there's only one value per field
     * (no widełki/factors to merge): a null incoming value just leaves the existing one. */
    public void refreshFromAi(String category, Double price, String unit) {
        this.category = category != null ? category : this.category;
        this.price = price != null ? price : this.price;
        this.unit = unit != null ? unit : this.unit;
        this.updatedAt = Instant.now();
    }
}
