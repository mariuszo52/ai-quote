package com.aiquote.backend.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AnthropicAiClient implements AiClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    // Etap 20: the autoconfigured RestClient.Builder has no timeout by default, so a
    // stalled connection to Anthropic would otherwise block a request thread
    // indefinitely instead of surfacing as the "awaria AI"/"timeout AI" error paths
    // already handled by callers (QuoteStreamingHandler, DraftQuoteGenerator, etc.).
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int maxTokens;

    public AnthropicAiClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.ai.anthropic.api-key}") String apiKey,
            @Value("${app.ai.anthropic.model}") String model,
            @Value("${app.ai.anthropic.max-tokens}") int maxTokens) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.maxTokens = maxTokens;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        this.restClient = restClientBuilder
                .baseUrl("https://api.anthropic.com/v1")
                .requestFactory(requestFactory)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
    }

    @Override
    public AiTurnResult sendMessage(String systemPrompt, List<AiMessage> messages, List<AiTool> tools) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("system", systemPrompt);
        requestBody.set("messages", toMessagesJson(messages));
        if (!tools.isEmpty()) {
            requestBody.set("tools", toToolsJson(tools));
        }

        String responseBody = restClient.post()
                .uri("/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(writeJson(requestBody))
                .retrieve()
                .body(String.class);

        return parseResponse(readJson(responseBody));
    }

    /**
     * Spring Boot 4's autoconfigured RestClient message converters are wired for Jackson 3
     * (tools.jackson); handing them a Jackson 2 (com.fasterxml.jackson) ObjectNode directly
     * gets it serialized via reflection instead of proper tree serialization, silently
     * dropping fields like "model". Serializing/parsing manually with our own Jackson 2
     * ObjectMapper and exchanging plain strings sidesteps that converter entirely.
     */
    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Anthropic request", e);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse Anthropic response", e);
        }
    }

    private ArrayNode toMessagesJson(List<AiMessage> messages) {
        ArrayNode array = objectMapper.createArrayNode();
        for (AiMessage message : messages) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", message.role() == AiRole.USER ? "user" : "assistant");
            node.set("content", toContentJson(message.content()));
            array.add(node);
        }
        return array;
    }

    private ArrayNode toContentJson(List<AiContentBlock> blocks) {
        ArrayNode array = objectMapper.createArrayNode();
        for (AiContentBlock block : blocks) {
            array.add(toBlockJson(block));
        }
        return array;
    }

    private ObjectNode toBlockJson(AiContentBlock block) {
        ObjectNode node = objectMapper.createObjectNode();
        switch (block) {
            case AiTextBlock text -> {
                node.put("type", "text");
                node.put("text", text.text());
            }
            case AiToolUseBlock toolUse -> {
                node.put("type", "tool_use");
                node.put("id", toolUse.id());
                node.put("name", toolUse.name());
                node.set("input", toolUse.input());
            }
            case AiToolResultBlock toolResult -> {
                node.put("type", "tool_result");
                node.put("tool_use_id", toolResult.toolUseId());
                node.put("content", toolResult.content());
            }
            case AiImageBlock image -> {
                node.put("type", "image");
                ObjectNode source = objectMapper.createObjectNode();
                source.put("type", "base64");
                source.put("media_type", image.mediaType());
                source.put("data", image.base64Data());
                node.set("source", source);
            }
        }
        return node;
    }

    private ArrayNode toToolsJson(List<AiTool> tools) {
        ArrayNode array = objectMapper.createArrayNode();
        for (AiTool tool : tools) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("name", tool.name());
            node.put("description", tool.description());
            node.set("input_schema", tool.inputSchema());
            array.add(node);
        }
        return array;
    }

    private AiTurnResult parseResponse(JsonNode response) {
        List<AiContentBlock> blocks = new ArrayList<>();
        for (JsonNode blockNode : response.path("content")) {
            String type = blockNode.path("type").asText();
            if ("text".equals(type)) {
                blocks.add(new AiTextBlock(blockNode.path("text").asText()));
            } else if ("tool_use".equals(type)) {
                blocks.add(new AiToolUseBlock(
                        blockNode.path("id").asText(),
                        blockNode.path("name").asText(),
                        blockNode.path("input")));
            }
        }
        String stopReason = response.path("stop_reason").isMissingNode()
                ? null
                : response.path("stop_reason").asText();
        return new AiTurnResult(blocks, stopReason);
    }
}
