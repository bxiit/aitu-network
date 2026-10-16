package aitu.network.aitunetwork.service;

import aitu.network.aitunetwork.model.dto.chat.WSMessage;
import aitu.network.aitunetwork.model.entity.User;
import aitu.network.aitunetwork.model.entity.chat.ChatMessage;
import aitu.network.aitunetwork.model.enums.MessageStatus;
import aitu.network.aitunetwork.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final MongoTemplate mongoTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final Executor executor;

    @Value("${secure-talk.chat.user-destination}")
    private String destination;

    public ChatMessage save(ChatMessage message) {
        message.setStatus(MessageStatus.DELIVERED);
        message.setReadBy(new ArrayList<>());
        chatMessageRepository.save(message);
        return message;
    }

    /**
     * Messages in the chat that the user hasn't read: sent by someone else and not in their readBy.
     * Messages from before per-user tracking (no readBy) count as unread only while still DELIVERED.
     */
    private static Criteria unreadBy(String chatId, String userId) {
        return Criteria.where(ChatMessage.Fields.chatId).is(chatId)
                .and(ChatMessage.Fields.senderId).ne(userId)
                .and(ChatMessage.Fields.readBy).ne(userId)
                .orOperator(
                        Criteria.where(ChatMessage.Fields.readBy).exists(true),
                        Criteria.where(ChatMessage.Fields.status).is(MessageStatus.DELIVERED)
                );
    }

    public long countNewMessages(String chatId, User user) {
        return mongoTemplate.count(Query.query(unreadBy(chatId, user.getId())), ChatMessage.class);
    }

    public List<ChatMessage> findChatMessages(String chatId) {
        return chatMessageRepository.findByChatIdOrderByCreatedAt(chatId);
    }

    /** Marks everything the reader hasn't read in the chat as read; returns those messages (id and senderId). */
    public List<ChatMessage> markChatAsRead(String chatId, String readerId) {
        Query unread = Query.query(unreadBy(chatId, readerId));
        unread.fields().include(ChatMessage.Fields.id, ChatMessage.Fields.senderId);
        List<ChatMessage> messages = mongoTemplate.find(unread, ChatMessage.class);
        if (messages.isEmpty()) {
            return messages;
        }
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("_id").in(messages.stream().map(ChatMessage::getId).toList())),
                new Update().addToSet(ChatMessage.Fields.readBy, readerId).set(ChatMessage.Fields.status, MessageStatus.RECEIVED),
                ChatMessage.class
        );
        return messages;
    }

    public void sendWSMessageToUsers(Collection<String> users, WSMessage message) {
        CompletableFuture.runAsync(() -> users.forEach(user -> sendToUser(user, message)), executor);
    }

    private void sendToUser(String userId, WSMessage WSMessage) {
        messagingTemplate.convertAndSendToUser(
                userId, destination,
                WSMessage
        );
    }
}