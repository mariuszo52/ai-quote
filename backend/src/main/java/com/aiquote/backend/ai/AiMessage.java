package com.aiquote.backend.ai;

import java.util.List;

public record AiMessage(AiRole role, List<AiContentBlock> content) {

    public static AiMessage userText(String text) {
        return new AiMessage(AiRole.USER, List.of(new AiTextBlock(text)));
    }

    public static AiMessage assistant(List<AiContentBlock> content) {
        return new AiMessage(AiRole.ASSISTANT, content);
    }
}
