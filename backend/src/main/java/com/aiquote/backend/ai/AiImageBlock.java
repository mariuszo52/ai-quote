package com.aiquote.backend.ai;

/**
 * Inline image sent to a vision-capable model. base64Data is the raw base64-encoded
 * image bytes (no data: URI prefix) — Anthropic's "base64" image source format.
 */
public record AiImageBlock(String mediaType, String base64Data) implements AiContentBlock {
}
