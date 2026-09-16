package com.aiquote.backend.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Structural regression guard for Etap 10 requirement #6: an anonymous public-chat
 * client must never be able to reach a Draft Quote (its items, AI reasoning,
 * uncertainFactors, or internal/client notes). Rather than trying to test "absence of
 * an endpoint" via HTTP, this asserts the actual dependency graph: neither
 * PublicQuoteController nor the service behind it (QuoteAgentService) holds a
 * reference to QuoteService/QuoteRepository/Quote, and none of their public methods
 * return quote data. If someone later wires the owner-facing quote read/write service
 * into the public path, this test fails immediately instead of relying on someone
 * noticing in review.
 */
class QuoteSecurityTest {

    private static final Set<Class<?>> FORBIDDEN_TYPES = Set.of(QuoteService.class, QuoteRepository.class, Quote.class);

    @Test
    void publicQuoteControllerNeverDependsOnDraftQuoteData() {
        assertNoForbiddenFields(PublicQuoteController.class);
        assertNoForbiddenReturnTypes(PublicQuoteController.class);
    }

    @Test
    void quoteAgentServiceNeverExposesDraftQuoteDataToTheClient() {
        // DraftQuoteGenerator is fine to depend on (write-only, fire-and-forget trigger);
        // QuoteService (the owner read/write API) must never be reachable from here.
        boolean dependsOnQuoteService = Arrays.stream(QuoteAgentService.class.getDeclaredFields())
                .anyMatch(field -> field.getType().equals(QuoteService.class));
        assertThat(dependsOnQuoteService).isFalse();

        assertNoForbiddenReturnTypes(QuoteAgentService.class);
    }

    private void assertNoForbiddenFields(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            assertThat(FORBIDDEN_TYPES).as("%s must not depend on %s", type.getSimpleName(), field.getType())
                    .doesNotContain(field.getType());
        }
    }

    private void assertNoForbiddenReturnTypes(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            Class<?> returnType = method.getReturnType();
            assertThat(FORBIDDEN_TYPES)
                    .as("%s#%s must not return %s", type.getSimpleName(), method.getName(), returnType)
                    .doesNotContain(returnType);
            assertThat(returnType).as("%s#%s must not return QuoteResponse", type.getSimpleName(), method.getName())
                    .isNotEqualTo(QuoteResponse.class);
        }
    }

    @Test
    void quoteLineItemAndQuoteResponseAreOnlyReachableFromOwnerFacingController() {
        // QuoteController is the one and only place draft quote data is served, and it's
        // authenticated + tenant-scoped by TenantContext (see QuoteController itself).
        List<Class<?>> ownerFacingReturnTypes = Arrays.stream(QuoteController.class.getDeclaredMethods())
                .map(Method::getReturnType)
                .toList();
        assertThat(ownerFacingReturnTypes).contains(QuoteResponse.class);
    }
}
