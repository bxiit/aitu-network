package aitu.network.aitunetwork.service;

import aitu.network.aitunetwork.common.exception.BadRequestException;
import aitu.network.aitunetwork.common.exception.ConflictException;
import aitu.network.aitunetwork.config.security.CustomUserDetailsService;
import aitu.network.aitunetwork.config.security.JwtService;
import aitu.network.aitunetwork.model.dto.chat.EncryptedPrivateKeyDto;
import aitu.network.aitunetwork.model.dto.chat.KeyPairSetupRequest;
import aitu.network.aitunetwork.model.entity.User;
import aitu.network.aitunetwork.model.entity.chat.ChatRoom;
import aitu.network.aitunetwork.repository.UserRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KeySetupTest {
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final AuthService authService = new AuthService(mock(UserRepository.class), mock(PasswordEncoder.class),
            mock(AuthenticationManager.class), mock(JwtService.class), mock(CustomUserDetailsService.class),
            mock(Executor.class), mock(MailService.class), mongoTemplate);

    private static KeyPairSetupRequest request(String publicKey) {
        return new KeyPairSetupRequest(publicKey, new EncryptedPrivateKeyDto("ZW5j", "c2FsdA==", "aXY="));
    }

    private static String validPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
    }

    private void updateMatches(long modified) {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(User.class)))
                .thenReturn(UpdateResult.acknowledged(modified, modified, null));
    }

    @Test
    @DisplayName("An account without keys can set them up")
    void setsKeysForAccountWithout() throws Exception {
        updateMatches(1);

        assertDoesNotThrow(() -> authService.setUpKeys("user-1", request(validPublicKey())));
    }

    @Test
    @DisplayName("Existing keys can't be replaced")
    void refusesToReplaceKeys() throws Exception {
        updateMatches(0); // the "no keys yet" filter matched nothing

        assertThrows(ConflictException.class, () -> authService.setUpKeys("user-1", request(validPublicKey())));
    }

    @Test
    @DisplayName("A malformed public key is rejected before touching the database")
    void rejectsInvalidPublicKey() {
        assertThrows(BadRequestException.class, () -> authService.setUpKeys("user-1", request("not-a-key")));

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    @DisplayName("Fetching a chat's keys reports a participant without keys instead of crashing")
    void chatKeysWithKeylessParticipant() {
        User me = User.builder().id("me").publicKey("PUB").build();
        User legacy = User.builder().id("legacy").build();
        ChatRoomService chatRoomService = mock(ChatRoomService.class);
        UserService userService = mock(UserService.class);
        when(chatRoomService.findChatRoom("chat-1"))
                .thenReturn(ChatRoom.builder().chatId("chat-1").participants(new ArrayList<>(List.of(me, legacy))).build());
        when(userService.getById("me")).thenReturn(me);
        ChatService chatService = new ChatService(mock(ChatMessageService.class), chatRoomService, userService,
                mongoTemplate, mock(FileService.class));

        assertThrows(ConflictException.class, () -> chatService.getChatParticipantsPublicKeys("chat-1", "me"));
    }
}
