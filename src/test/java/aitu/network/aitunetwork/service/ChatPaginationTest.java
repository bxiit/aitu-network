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
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;

/** Verifies the paginated chat list is ordered by latest message (most recent first) and pages correctly. */
public class ChatPaginationTest extends MongoTestContainer {

    @Autowired
    private ChatService chatService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    private User user;

    @TestConfiguration
    static class Config {
        @Bean
        ChatService chatService(MongoTemplate mongoTemplate) {
            // findChats only uses the MongoTemplate; the other collaborators aren't exercised here
            return new ChatService(mock(ChatMessageService.class), mock(ChatRoomService.class),
                    mock(UserService.class), mongoTemplate, mock(FileService.class));
        }
    }

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().email("me@gmail.com").username("me").build());
        User other = userRepository.save(User.builder().email("other@gmail.com").username("other").build());

        // three rooms; a message in each at a distinct time so ordering is unambiguous
        room("recent", other);
        room("middle", other);
        room("oldest", other);
        messageAt("recent", Instant.parse("2026-01-03T00:00:00Z"));
        messageAt("middle", Instant.parse("2026-01-02T00:00:00Z"));
        messageAt("oldest", Instant.parse("2026-01-01T00:00:00Z"));
    }

    private void room(String chatId, User other) {
        chatRoomRepository.save(ChatRoom.builder()
                .chatId(chatId).title(chatId).chatRoomType(ChatRoomType.GROUP).empty(false)
                .participants(List.of(user, other)).build());
    }

    private void messageAt(String chatId, Instant createdAt) {
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder().chatId(chatId).senderId("other").build());
        // @CreatedDate stamps createdAt on insert, so set the controlled time afterwards
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(saved.getId())),
                Update.update("createdAt", createdAt), ChatMessage.class);
    }

    private List<String> chatIds(List<ChatRoomsWithMessages> page) {
        return page.stream().map(c -> c.getChatRoom().chatId()).toList();
    }

    @Test
    @DisplayName("First page is the most recent conversations, in descending last-message order")
    void firstPageMostRecentFirst() {
        List<ChatRoomsWithMessages> page = chatService.findChats(user, PageRequest.of(0, 2));

        assertThat(page, hasSize(2));
        assertThat(chatIds(page), contains("recent", "middle"));
        // the room carries its own messages
        assertThat(page.getFirst().getMessages(), hasSize(1));
    }

    @Test
    @DisplayName("The next page continues after the first, with no overlap")
    void secondPageContinues() {
        assertThat(chatIds(chatService.findChats(user, PageRequest.of(1, 2))), contains("oldest"));
    }

    @AfterEach
    void clearUp() {
        userRepository.deleteAll();
        chatRoomRepository.deleteAll();
        chatMessageRepository.deleteAll();
    }
}
