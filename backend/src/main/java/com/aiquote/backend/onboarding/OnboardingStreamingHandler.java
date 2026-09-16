package com.aiquote.backend.onboarding;

import com.aiquote.backend.common.ApiException;
import com.aiquote.backend.common.SseStreamer;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Separate bean (not a method on OnboardingService) so @Async actually goes through
 * the Spring proxy — see KnowledgeSourceProcessor for the same reasoning from Etap 3.
 */
@Component
@RequiredArgsConstructor
public class OnboardingStreamingHandler {

    private final OnboardingService onboardingService;

    @Async
    public void stream(SseEmitter emitter, Long companyId, Long conversationId, String userText) {
        try {
            SendMessageResponse response = onboardingService.sendMessage(companyId, conversationId, userText);
            SseStreamer.streamWords(emitter, response.reply());
            emitter.send(SseEmitter.event().name("done").data(response));
            emitter.complete();
        } catch (ApiException e) {
            SseStreamer.sendError(emitter, e.getMessage());
        } catch (IOException e) {
            emitter.completeWithError(e);
        } catch (Exception e) {
            SseStreamer.sendError(emitter, "Wystąpił nieoczekiwany błąd.");
        }
    }
}
