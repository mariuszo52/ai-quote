package com.aiquote.backend.quote;

import com.aiquote.backend.ai.AiContentBlock;
import com.aiquote.backend.ai.AiImageBlock;
import com.aiquote.backend.ai.AiMessage;
import com.aiquote.backend.ai.AiRole;
import com.aiquote.backend.ai.AiTextBlock;
import com.aiquote.backend.conversation.Attachment;
import com.aiquote.backend.conversation.AttachmentRepository;
import com.aiquote.backend.conversation.Message;
import com.aiquote.backend.conversation.MessageRepository;
import com.aiquote.backend.conversation.MessageRole;
import com.aiquote.backend.file.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the AI-facing conversation history (text + photos) from storage. Shared by
 * QuoteAgentService (live client chat) and DraftQuoteGenerator (post-contact quote
 * generation) — pulled out on its own so neither has to depend on the other just to
 * reuse this, which would otherwise create a circular bean dependency since
 * DraftQuoteGenerator is triggered from QuoteAgentService.
 */
@Component
@RequiredArgsConstructor
public class ConversationHistoryReader {

    private final MessageRepository messageRepository;
    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;

    public List<AiMessage> buildHistory(Long conversationId) {
        List<AiMessage> aiMessages = new ArrayList<>();
        for (Message message : messageRepository.findByConversationIdOrderByIdAsc(conversationId)) {
            if (message.getRole() == MessageRole.USER) {
                List<AiContentBlock> blocks = new ArrayList<>();
                blocks.add(new AiTextBlock(message.getContent()));
                for (Attachment attachment : attachmentRepository.findByMessageId(message.getId())) {
                    blocks.add(toImageBlock(attachment));
                }
                aiMessages.add(new AiMessage(AiRole.USER, blocks));
            } else if (message.getRole() == MessageRole.ASSISTANT) {
                aiMessages.add(AiMessage.assistant(List.of(new AiTextBlock(message.getContent()))));
            }
        }
        return aiMessages;
    }

    private AiImageBlock toImageBlock(Attachment attachment) {
        try (InputStream in = storageService.retrieve(attachment.getStorageKey())) {
            String base64 = Base64.getEncoder().encodeToString(in.readAllBytes());
            String mediaType = attachment.getMimeType() != null ? attachment.getMimeType() : "image/jpeg";
            return new AiImageBlock(mediaType, base64);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read attachment for vision: " + attachment.getStorageKey(), e);
        }
    }
}
