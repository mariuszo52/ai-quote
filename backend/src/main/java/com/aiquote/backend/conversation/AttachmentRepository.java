package com.aiquote.backend.conversation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByConversationIdAndMessageIdIsNull(Long conversationId);

    List<Attachment> findByMessageId(Long messageId);

    List<Attachment> findByConversationIdOrderByIdAsc(Long conversationId);
}
