package com.aiquote.backend.onboarding;

import com.aiquote.backend.conversation.MessageDto;
import java.util.List;

public record StartSessionResponse(Long conversationId, List<MessageDto> messages) {
}
