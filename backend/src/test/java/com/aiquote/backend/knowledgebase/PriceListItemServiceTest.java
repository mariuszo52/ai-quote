package com.aiquote.backend.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Covers "Moje materiały" CRUD plus the AI-sourced upsert-by-name behavior shared by
 * document import (source UPLOADED) and the chat configurator (source CHAT). */
@ExtendWith(MockitoExtension.class)
class PriceListItemServiceTest {

    @Mock
    private PriceListItemRepository repository;

    private PriceListItemService service;

    @BeforeEach
    void setUp() {
        service = new PriceListItemService(repository);
    }

    private PriceListItem item(long id, long companyId, String name, PriceListItemSource source) {
        PriceListItem item = new PriceListItem(companyId, name, "Kategoria", 25.0, "szt", source);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Test
    void createsAManualItem() {
        when(repository.save(any())).thenAnswer(invocation -> {
            PriceListItem saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        PriceListItemResponse response = service.create(5L, new PriceListItemRequest("Rura PVC 50mm", "Rury", 25.0, "mb"));

        assertThat(response.name()).isEqualTo("Rura PVC 50mm");
        assertThat(response.source()).isEqualTo("MANUAL");
    }

    @Test
    void updateIsTenantScoped() {
        when(repository.findByIdAndCompanyId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, 1L, new PriceListItemRequest("x", null, null, null)))
                .isInstanceOf(PriceListItemNotFoundException.class);
    }

    @Test
    void updatePreservesOriginalSource() {
        PriceListItem existing = item(1L, 5L, "Rura PVC 50mm", PriceListItemSource.UPLOADED);
        when(repository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(existing));

        PriceListItemResponse response = service.update(5L, 1L, new PriceListItemRequest("Rura PVC 63mm", "Rury", 30.0, "mb"));

        assertThat(response.name()).isEqualTo("Rura PVC 63mm");
        assertThat(response.source()).isEqualTo("UPLOADED");
    }

    @Test
    void deleteIsTenantScoped() {
        when(repository.findByIdAndCompanyId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L, 1L)).isInstanceOf(PriceListItemNotFoundException.class);
    }

    @Test
    void upsertFromAiCreatesANewRowWhenNoMatchingNameExists() {
        when(repository.findByCompanyIdAndNameIgnoreCase(5L, "Klimatyzator Daikin X")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            PriceListItem saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });

        PriceListItemResponse response = service.upsertFromAi(5L, "Klimatyzator Daikin X", "Klimatyzacja", 3500.0, "szt", PriceListItemSource.CHAT);

        assertThat(response.source()).isEqualTo("CHAT");
        assertThat(response.price()).isEqualTo(3500.0);
    }

    @Test
    void upsertFromAiRefreshesAnExistingRowMatchedByNameInsteadOfDuplicating() {
        PriceListItem existing = item(3L, 5L, "rura pvc 50mm", PriceListItemSource.UPLOADED);
        ReflectionTestUtils.setField(existing, "price", null);
        when(repository.findByCompanyIdAndNameIgnoreCase(5L, "Rura PVC 50mm")).thenReturn(Optional.of(existing));

        PriceListItemResponse response = service.upsertFromAi(5L, "Rura PVC 50mm", null, 27.5, "mb", PriceListItemSource.CHAT);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.price()).isEqualTo(27.5);
        // Refreshing never overwrites a known source — this row is still the original UPLOADED row.
        assertThat(response.source()).isEqualTo("UPLOADED");
        verify(repository, never()).save(any());
    }

    @Test
    void listOrdersByMostRecentFirst() {
        when(repository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(item(1L, 5L, "A", PriceListItemSource.MANUAL)));

        assertThat(service.list(5L)).hasSize(1);
    }
}
