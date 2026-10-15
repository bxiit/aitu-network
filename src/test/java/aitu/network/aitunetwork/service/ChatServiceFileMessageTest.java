package aitu.network.aitunetwork.service;

import aitu.network.aitunetwork.common.exception.ForbiddenException;
import aitu.network.aitunetwork.model.entity.User;
import aitu.network.aitunetwork.model.entity.chat.ChatRoom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatServiceFileMessageTest {
    private final ChatRoomService chatRoomService = mock(ChatRoomService.class);
    private final FileService fileService = mock(FileService.class);
    private final ChatService chatService = new ChatService(
            mock(ChatMessageService.class), chatRoomService, mock(UserService.class), mock(MongoTemplate.class), fileService);

    private final User member = User.builder().id("member").build();
    private final User outsider = User.builder().id("outsider").build();
    private final MockMultipartFile file = new MockMultipartFile("file", "blob", "application/octet-stream", new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        ChatRoom chatRoom = ChatRoom.builder().chatId("chat-1").participants(new ArrayList<>(List.of(member))).build();
        when(chatRoomService.findChatRoom("chat-1")).thenReturn(chatRoom);
    }

    @Test
    @DisplayName("A user outside the chat can't upload into it, and nothing is stored")
    void uploadRejectsNonParticipant() {
        assertThrows(ForbiddenException.class, () -> chatService.uploadChatFile("chat-1", file, outsider));

        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("A participant's upload is stored under the chat and the authenticated uploader")
    void uploadStoresFileForChat() {
        when(fileService.uploadChatFile(file, "chat-1", "member")).thenReturn("file-1");

        assertThat(chatService.uploadChatFile("chat-1", file, member), equalTo("file-1"));
    }

    @Test
    @DisplayName("A user outside the chat can't download its files")
    void downloadRejectsNonParticipant() {
        assertThrows(ForbiddenException.class, () -> chatService.getChatFile("chat-1", "file-1", outsider));

        verifyNoInteractions(fileService);
    }
}
