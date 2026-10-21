package aitu.network.aitunetwork.service;

import aitu.network.aitunetwork.config.MongoTestContainer;
import aitu.network.aitunetwork.model.entity.chat.ChatMessage;
import aitu.network.aitunetwork.model.enums.MessageStatus;
import aitu.network.aitunetwork.repository.ChatMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;

/** Verifies the bulk per-chat unread aggregation that backs the single /rooms/unread-counts request. */
public class UnreadCountTest extends MongoTestContainer {

    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @TestConfiguration
    static class Config {
        @Bean
        ChatMessageService chatMessageService(ChatMessageRepository repository, MongoTemplate mongoTemplate) {
            // the fan-out collaborators aren't exercised by the count query
            return new ChatMessageService(repository, mongoTemplate, mock(SimpMessagingTemplate.class), Runnable::run);
        }
    }

    private void save(String chatId, String senderId, MessageStatus status, List<String> readBy) {
        chatMessageRepository.save(ChatMessage.builder()
                .chatId(chatId).senderId(senderId).status(status).readBy(readBy).build());
    }

    @Test
    @DisplayName("Counts per chat exclude the reader's own messages and ones they've already read")
    void countsUnreadPerChat() {
        save("chat-1", "alice", MessageStatus.DELIVERED, List.of());        // unread for reader
        save("chat-1", "alice", MessageStatus.DELIVERED, List.of());        // unread for reader
        save("chat-1", "reader", MessageStatus.DELIVERED, List.of());       // reader's own: excluded
        save("chat-1", "alice", MessageStatus.RECEIVED, List.of("reader")); // already read: excluded
        save("chat-2", "bob", MessageStatus.DELIVERED, List.of());          // unread for reader

        Map<String, Long> counts = chatMessageService.countUnreadByChat(List.of("chat-1", "chat-2"), "reader");

        assertThat(counts.get("chat-1"), equalTo(2L));
        assertThat(counts.get("chat-2"), equalTo(1L));
    }

    @Test
    @DisplayName("A chat not in the requested set is never counted, even with unread messages")
    void scopesToRequestedChats() {
        save("chat-1", "alice", MessageStatus.DELIVERED, List.of());
        save("other", "alice", MessageStatus.DELIVERED, List.of());

        Map<String, Long> counts = chatMessageService.countUnreadByChat(List.of("chat-1"), "reader");

        assertThat(counts.get("chat-1"), equalTo(1L));
        assertThat(counts.containsKey("other"), equalTo(false));
    }

    @Test
    @DisplayName("Chats with nothing unread are absent from the map; no chats asked gives an empty map")
    void omitsFullyReadAndEmptyInput() {
        save("chat-1", "alice", MessageStatus.RECEIVED, List.of("reader"));

        assertThat(chatMessageService.countUnreadByChat(List.of("chat-1"), "reader"), anEmptyMap());
        assertThat(chatMessageService.countUnreadByChat(List.of(), "reader"), anEmptyMap());
    }

    @AfterEach
    void clearUp() {
        chatMessageRepository.deleteAll();
    }
}
