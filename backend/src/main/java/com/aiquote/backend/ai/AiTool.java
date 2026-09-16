package com.aiquote.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;

public record AiTool(String name, String description, JsonNode inputSchema) {
}
