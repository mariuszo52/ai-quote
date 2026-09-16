package com.aiquote.backend.offer;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiquote.backend.quote.Quote;
import com.aiquote.backend.quote.QuoteRepository;
import com.aiquote.backend.quote.QuoteService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Structural regression guard for Etap 12's "no client access to internal data"
 * requirement, same technique as quote.QuoteSecurityTest: assert the actual dependency
 * graph and return types instead of trying to test "absence" over HTTP.
 *
 * PublicOfferController must never be able to reach Quote/QuoteService/QuoteRepository
 * (the AI reasoning, confidence, uncertainFactors, internalNote, changeLog all live
 * there, never on Offer) and must never return the internal Offer entity itself (which
 * carries status/sentAt/lastSendError — operational metadata the client has no business
 * seeing) — only PublicOfferResponse.
 */
class OfferSecurityTest {

    private static final Set<Class<?>> FORBIDDEN_TYPES = Set.of(Quote.class, QuoteService.class, QuoteRepository.class, Offer.class);

    @Test
    void publicOfferControllerNeverDependsOnQuoteOrInternalOfferData() {
        for (Field field : PublicOfferController.class.getDeclaredFields()) {
            assertThat(FORBIDDEN_TYPES).as("PublicOfferController must not depend on %s", field.getType())
                    .doesNotContain(field.getType());
        }
        for (Method method : PublicOfferController.class.getDeclaredMethods()) {
            assertThat(FORBIDDEN_TYPES).as("PublicOfferController#%s must not return %s", method.getName(), method.getReturnType())
                    .doesNotContain(method.getReturnType());
        }
    }

    @Test
    void offerPdfGeneratorNeverDependsOnQuote() {
        for (Field field : OfferPdfGenerator.class.getDeclaredFields()) {
            assertThat(field.getType()).as("OfferPdfGenerator must not depend on Quote").isNotEqualTo(Quote.class);
        }
        for (Method method : OfferPdfGenerator.class.getDeclaredMethods()) {
            for (Class<?> paramType : method.getParameterTypes()) {
                assertThat(paramType).as("OfferPdfGenerator#%s must not take a Quote parameter", method.getName()).isNotEqualTo(Quote.class);
            }
        }
    }

    @Test
    void publicOfferResponseHasNoInternalSendMetadataFields() {
        Set<String> fieldNames = Arrays.stream(PublicOfferResponse.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertThat(fieldNames).doesNotContain("status", "sentAt", "lastSendError", "publicToken", "quoteId", "companyId");
    }
}
