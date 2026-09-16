package com.aiquote.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;

public record AiToolUseBlock(String id, String name, JsonNode input) implements AiContentBlock {
}
