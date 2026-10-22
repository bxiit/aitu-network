package aitu.network.aitunetwork.service;

import aitu.network.aitunetwork.config.MongoTestContainer;
import aitu.network.aitunetwork.model.dto.chat.ChatRoomsWithMessages;
import aitu.network.aitunetwork.model.entity.User;
import aitu.network.aitunetwork.model.entity.chat.ChatMessage;
import aitu.network.aitunetwork.model.entity.chat.ChatRoom;
import aitu.network.aitunetwork.model.entity.chat.ChatRoomType;
import aitu.network.aitunetwork.repository.ChatMessageRepository;
import aitu.network.aitunetwork.repository.ChatRoomRepository;
import aitu.network.aitunetwork.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;

/** Verifies message pagination: cursor-based history fetch, and the chat-list lookup capping messages per room. */
public class MessagePaginationTest extends MongoTestContainer {

    @Autowired
    private ChatService chatService;
    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    private final LocalDateTime base = LocalDateTime.parse("2026-01-01T00:00:00");

    @TestConfiguration
    static class Config {
        @Bean
        ChatService chatService(MongoTemplate mongoTemplate) {
            return new ChatService(mock(ChatMessageService.class), mock(ChatRoomService.class),
                    mock(UserService.class), mongoTemplate, mock(FileService.class));
        }

        @Bean
        ChatMessageService chatMessageService(ChatMessageRepository repository, MongoTemplate mongoTemplate) {
            return new ChatMessageService(repository, mongoTemplate, mock(SimpMessagingTemplate.class), Runnable::run);
        }
    }

    /** message "mNN" in chat, createdAt = base + i minutes (so later index = newer). */
    private void message(String chatId, int i) {
        String id = "m" + String.format("%02d", i);
        chatMessageRepository.save(ChatMessage.builder().id(id).chatId(chatId).senderId("other").build());
        // @CreatedDate stamps createdAt on insert, so set the controlled time afterwards
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)),
                Update.update("createdAt", base.plusMinutes(i)), ChatMessage.class);
    }

    private List<String> ids(List<ChatMessage> messages) {
        return messages.stream().map(ChatMessage::getId).toList();
    }

    @Test
    @DisplayName("Latest page returns the newest N messages, oldest-to-newest")
    void latestPageAscending() {
        for (int i = 0; i < 5; i++) message("c", i); // m00..m04

        assertThat(ids(chatMessageService.findChatMessages("c", null, 3)), contains("m02", "m03", "m04"));
    }

    @Test
    @DisplayName("Older page returns the messages just before the cursor, oldest-to-newest")
    void olderPageBeforeCursor() {
        for (int i = 0; i < 5; i++) message("c", i); // m00..m04

        // everything strictly older than m03's time -> m00, m01, m02 ; take the newest 2 of those
        assertThat(ids(chatMessageService.findChatMessages("c", base.plusMinutes(3), 2)), contains("m01", "m02"));
    }

    @Test
    @DisplayName("A chat only returns its own messages")
    void scopedToChat() {
        message("c", 0);
        message("other", 1);

        assertThat(ids(chatMessageService.findChatMessages("c", null, 10)), contains("m00"));
    }

    @Test
    @DisplayName("The chat list caps each room's messages at one page, keeping the newest")
    void chatListCapsMessagesPerRoom() {
        User other = userRepository.save(User.builder().email("o@g.com").username("o").build());
        User user = userRepository.save(User.builder().email("me@g.com").username("me").build());
        chatRoomRepository.save(ChatRoom.builder().chatId("c").title("c").chatRoomType(ChatRoomType.GROUP)
                .empty(false).participants(List.of(user, other)).build());

        int total = ChatMessageService.MESSAGE_PAGE_SIZE + 5;
        for (int i = 0; i < total; i++) message("c", i);

        List<ChatRoomsWithMessages> page = chatService.findChats(user, PageRequest.of(0, 10));
        List<ChatMessage> messages = page.getFirst().getMessages();

        assertThat(messages, hasSize(ChatMessageService.MESSAGE_PAGE_SIZE));
        // kept the newest page, ascending: first is m05 (total-30 = 5), last is the very newest
        assertThat(messages.getFirst().getId(), org.hamcrest.Matchers.equalTo("m05"));
        assertThat(messages.getLast().getId(), org.hamcrest.Matchers.equalTo("m" + String.format("%02d", total - 1)));
    }

    @AfterEach
    void clearUp() {
        userRepository.deleteAll();
        chatRoomRepository.deleteAll();
        chatMessageRepository.deleteAll();
    }
}
