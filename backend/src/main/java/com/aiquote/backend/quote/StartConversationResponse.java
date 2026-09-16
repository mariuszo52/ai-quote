package com.aiquote.backend.quote;

import java.util.List;

public record StartConversationResponse(
        Long conversationId, String token, String companyName, String greeting, List<String> options) {
}
