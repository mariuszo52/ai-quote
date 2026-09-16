package com.aiquote.backend.common;

import java.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Streams an already-computed AI reply to the client word-by-word over SSE. This is
 * deliberately NOT token-level streaming from the model itself — the existing
 * OnboardingService/QuoteAgentService tool-use loops run to completion first (unchanged,
 * already-verified logic), and only the final text is revealed progressively. Real
 * provider-level streaming would require parsing Anthropic's raw SSE format and
 * threading partial tool-input JSON through the tool loop, which is a much larger,
 * riskier change for a UX-only improvement — this gets the same "typing" feel for the
 * user with far less surface area for bugs.
 */
public final class SseStreamer {

    private static final long DELAY_MS_PER_CHUNK = 20;

    private SseStreamer() {
    }

    public static void streamWords(SseEmitter emitter, String text) throws IOException {
        String[] words = text.split("(?<=\\s)");
        for (String word : words) {
            emitter.send(SseEmitter.event().name("delta").data(word));
            try {
                Thread.sleep(DELAY_MS_PER_CHUNK);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
