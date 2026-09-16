package com.aiquote.backend.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyPricingProfileServiceTest {

    @Mock
    private CompanyPricingProfileRepository repository;

    private CompanyPricingProfileService service;

    @BeforeEach
    void setUp() {
        service = new CompanyPricingProfileService(repository, new ObjectMapper());
    }

    @Test
    void getDataParsesLegacyFreeTextShapeIntoGeneralNotes() {
        CompanyPricingProfile profile = existingProfileWithJson("{\"summary\":\"Stawka 100 zl/h\"}");
        when(repository.findByCompanyId(1L)).thenReturn(Optional.of(profile));

        PricingProfileData data = service.getData(1L);

        assertThat(data.services()).isEmpty();
        assertThat(data.generalNotes()).isEqualTo("Stawka 100 zl/h");
    }

    @Test
    void getDataParsesCurrentStructuredShape() {
        CompanyPricingProfile profile = existingProfileWithJson("{\"services\":[{\"name\":\"Usluga\"}],\"generalNotes\":null}");
        when(repository.findByCompanyId(1L)).thenReturn(Optional.of(profile));

        PricingProfileData data = service.getData(1L);

        assertThat(data.services()).hasSize(1);
        assertThat(data.services().get(0).name()).isEqualTo("Usluga");
    }

    @Test
    void getDataOnEmptyProfileReturnsEmptyData() {
        when(repository.findByCompanyId(1L)).thenReturn(Optional.of(new CompanyPricingProfile(1L)));

        PricingProfileData data = service.getData(1L);

        assertThat(data.services()).isEmpty();
        assertThat(data.generalNotes()).isNull();
    }

    @Test
    void replaceManuallySetsManuallyEditedAt() {
        CompanyPricingProfile profile = new CompanyPricingProfile(1L);
        when(repository.findByCompanyId(1L)).thenReturn(Optional.of(profile));

        service.replaceManually(1L, new PricingProfileData(List.of(), "notatka"));

        assertThat(profile.getManuallyEditedAt()).isNotNull();
    }

    @Test
    void mergeAiUpdateDoesNotSetManuallyEditedAt() {
        CompanyPricingProfile profile = new CompanyPricingProfile(1L);
        when(repository.findByCompanyId(1L)).thenReturn(Optional.of(profile));

        service.mergeAiUpdate(1L, new PricingProfileData(List.of(), "notatka"));

        assertThat(profile.getManuallyEditedAt()).isNull();
    }

    @Test
    void mergeAiUpdatePreservesFieldsOmittedByIncomingUpdate() {
        CompanyPricingProfile profile = existingProfileWithJson(
                "{\"services\":[{\"name\":\"Malowanie\",\"basePrice\":30.0,\"unit\":\"m2\"}],\"generalNotes\":null}");
        when(repository.findByCompanyId(1L)).thenReturn(Optional.of(profile));

        PricingServiceEntry incomingEntry = new PricingServiceEntry(
                "Malowanie", null, null, null, null, null, null, List.of("wysokość sufitu"), null, null);
        service.mergeAiUpdate(1L, new PricingProfileData(List.of(incomingEntry), null));

        PricingProfileData result = service.getData(1L);
        assertThat(result.services()).hasSize(1);
        assertThat(result.services().get(0).basePrice()).isEqualTo(30.0); // preserved, not wiped by omission
        assertThat(result.services().get(0).unit()).isEqualTo("m2"); // preserved
        assertThat(result.services().get(0).factors()).containsExactly("wysokość sufitu"); // newly added
    }

    private CompanyPricingProfile existingProfileWithJson(String json) {
        CompanyPricingProfile profile = new CompanyPricingProfile(1L);
        profile.updateFromAi(json);
        return profile;
    }
}
