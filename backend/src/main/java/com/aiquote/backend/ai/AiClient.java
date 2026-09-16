package com.aiquote.backend.ai;

import java.util.List;

/**
 * Provider-agnostic chat/tool-use abstraction. AnthropicAiClient is the only
 * implementation for now; swapping providers means adding another implementation,
 * not touching any calling code.
 */
public interface AiClient {

    AiTurnResult sendMessage(String systemPrompt, List<AiMessage> messages, List<AiTool> tools);
}
