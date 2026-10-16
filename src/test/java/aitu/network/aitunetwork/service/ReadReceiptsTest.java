package aitu.network.aitunetwork.service;

import aitu.network.aitunetwork.common.exception.ForbiddenException;
import aitu.network.aitunetwork.model.dto.WSMessageType;
import aitu.network.aitunetwork.model.dto.chat.MessagesRead;
import aitu.network.aitunetwork.model.dto.chat.WSMessage;
import aitu.network.aitunetwork.model.entity.User;
import aitu.network.aitunetwork.model.entity.chat.ChatMessage;
import aitu.network.aitunetwork.model.entity.chat.ChatRoom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReadReceiptsTest {
    private final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    private final ChatRoomService chatRoomService = mock(ChatRoomService.class);
    private final ChatService chatService = new ChatService(chatMessageService, chatRoomService,
            mock(UserService.class), mock(MongoTemplate.class), mock(FileService.class));

    private final User reader = User.builder().id("reader").build();
    private final User outsider = User.builder().id("outsider").build();

    @BeforeEach
    void setUp() {
        User alice = User.builder().id("alice").build();
        User bob = User.builder().id("bob").build();
        when(chatRoomService.findChatRoom("group-1")).thenReturn(ChatRoom.builder().chatId("group-1")
                .participants(new ArrayList<>(List.of(reader, alice, bob))).build());
    }

    private static ChatMessage message(String id, String senderId) {
        return ChatMessage.builder().id(id).senderId(senderId).build();
    }

    @Test
    @DisplayName("Only a participant can mark a chat as read")
    void outsiderCannotMarkRead() {
        assertThrows(ForbiddenException.class, () -> chatService.markChatAsRead("group-1", outsider));

        verifyNoInteractions(chatMessageService);
    }

    @Test
    @DisplayName("Each sender is told which of their messages were read, and the reader's other sessions too")
    @SuppressWarnings("unchecked")
    void notifiesSendersAndReader() {
        when(chatMessageService.markChatAsRead("group-1", "reader"))
                .thenReturn(List.of(message("m1", "alice"), message("m2", "bob"), message("m3", "alice")));

        chatService.markChatAsRead("group-1", reader);

        ArgumentCaptor<Collection<String>> recipients = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<WSMessage> events = ArgumentCaptor.forClass(WSMessage.class);
        verify(chatMessageService, times(3)).sendWSMessageToUsers(recipients.capture(), events.capture());

        Map<String, List<String>> idsByRecipient = new HashMap<>();
        for (int i = 0; i < events.getAllValues().size(); i++) {
            WSMessage event = events.getAllValues().get(i);
            assertThat(event.getType(), equalTo(WSMessageType.MESSAGES_READ));
            MessagesRead read = (MessagesRead) event.getMessage();
            assertThat(read.readerId(), equalTo("reader"));
            idsByRecipient.put(recipients.getAllValues().get(i).iterator().next(), read.messageIds());
        }
        assertThat(idsByRecipient.get("alice"), containsInAnyOrder("m1", "m3"));
        assertThat(idsByRecipient.get("bob"), containsInAnyOrder("m2"));
        assertThat(idsByRecipient.get("reader"), containsInAnyOrder("m1", "m2", "m3"));
    }

    @Test
    @DisplayName("Nothing new to read: no events")
    void nothingToRead() {
        when(chatMessageService.markChatAsRead("group-1", "reader")).thenReturn(List.of());

        chatService.markChatAsRead("group-1", reader);

        verify(chatMessageService, never()).sendWSMessageToUsers(any(), any());
    }

    @Test
    @DisplayName("Unread counts are only for participants")
    void countRequiresParticipant() {
        assertThrows(ForbiddenException.class, () -> chatService.countNewMessages("group-1", outsider));
    }
}
