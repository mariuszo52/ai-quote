package com.aiquote.backend.ai;

import java.util.List;
import java.util.stream.Collectors;

public record AiTurnResult(List<AiContentBlock> content, String stopReason) {

    public boolean requiresToolExecution() {
        return "tool_use".equals(stopReason);
    }

    public String extractText() {
        return content.stream()
                .filter(AiTextBlock.class::isInstance)
                .map(AiTextBlock.class::cast)
                .map(AiTextBlock::text)
                .collect(Collectors.joining("\n"));
    }

    public List<AiToolUseBlock> extractToolUses() {
        return content.stream()
                .filter(AiToolUseBlock.class::isInstance)
                .map(AiToolUseBlock.class::cast)
                .toList();
    }
}
