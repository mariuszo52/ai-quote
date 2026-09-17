package com.aiquote.backend.knowledgebase;

import com.aiquote.backend.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-facing (authenticated, tenant-scoped) "Moje materiały" CRUD — see
 * PriceListItemService's javadoc for how this relates to the cennik/services profile. */
@RestController
@RequestMapping("/api/price-list-items")
@RequiredArgsConstructor
public class PriceListItemController {

    private final PriceListItemService service;

    @GetMapping
    public List<PriceListItemResponse> list() {
        return service.list(TenantContext.currentCompanyId());
    }

    @PostMapping
    public PriceListItemResponse create(@Valid @RequestBody PriceListItemRequest request) {
        return service.create(TenantContext.currentCompanyId(), request);
    }

    @PutMapping("/{id}")
    public PriceListItemResponse update(@PathVariable Long id, @Valid @RequestBody PriceListItemRequest request) {
        return service.update(TenantContext.currentCompanyId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(TenantContext.currentCompanyId(), id);
        return ResponseEntity.noContent().build();
    }
}
