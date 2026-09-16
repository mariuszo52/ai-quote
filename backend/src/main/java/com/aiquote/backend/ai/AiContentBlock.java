package com.aiquote.backend.ai;

public sealed interface AiContentBlock permits AiTextBlock, AiToolUseBlock, AiToolResultBlock, AiImageBlock {
}
