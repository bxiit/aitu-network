package aitu.network.aitunetwork.controller;

import aitu.network.aitunetwork.common.annotations.CurrentUser;
import aitu.network.aitunetwork.config.security.CustomUserDetails;
import aitu.network.aitunetwork.model.dto.chat.ChatRoomDTO;
import aitu.network.aitunetwork.model.dto.chat.ChatRoomsWithMessages;
import aitu.network.aitunetwork.model.dto.chat.NewChatRoomDTO;
import aitu.network.aitunetwork.model.entity.chat.ChatMessage;
import aitu.network.aitunetwork.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserFetcher userFetcher;

    @GetMapping("/rooms/{chatId}/keys")
    public ResponseEntity<Map<String, String>> getChatParticipantsKeys(
            @PathVariable String chatId,
            @CurrentUser CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(chatService.getChatParticipantsPublicKeys(
                chatId,
                userDetails.user().getId()
        ));
    }

    @GetMapping("/rooms/{id}/messages/count")
    ResponseEntity<Map<String, Object>> countNewMessages(
            @PathVariable String id,
            @CurrentUser CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(chatService.countNewMessages(id, userDetails.user()));
    }

    @GetMapping("/rooms/{chatId}/messages")
    List<ChatMessage> findChatMessages(@PathVariable String chatId) {
        return chatService.findChatMessages(chatId);
    }

    @GetMapping({"/{userId}", "/"})
    List<ChatRoomsWithMessages> findChatMessages(
            @PathVariable(required = false) String userId,
            @CurrentUser CustomUserDetails userDetails
    ) {
        return chatService.findChats(userDetails.resolveUserId(userId, userFetcher));
    }

    @GetMapping(value = {"/rooms/{userId}", "/rooms/"})
    List<ChatRoomDTO> findUserChatRooms(
            @PathVariable(required = false) String userId,
            @CurrentUser CustomUserDetails userDetails
    ) {
        return chatService.findUserChatRooms(userDetails.resolveUserId(userId, userFetcher));
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    ChatRoomDTO createChatRoom(@Valid @RequestBody NewChatRoomDTO chatRoom,
                               @CurrentUser CustomUserDetails userDetails) {
        return chatService.createChatRoom(chatRoom, userDetails.user());
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("/rooms/{id}/participants/{participantId}")
    void addUserToChatRoom(@PathVariable String id, @PathVariable String participantId) {
        chatService.addParticipantToChatRoom(id, participantId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/rooms/{id}/participants/{participantId}")
    void deleteUserFromChatRoom(@PathVariable String id, @PathVariable String participantId) {
        chatService.deleteUserFromChatRoom(id, participantId);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/rooms/{chatId}/files")
    Map<String, String> uploadChatFile(
            @PathVariable String chatId,
            @RequestPart("file") MultipartFile file,
            @CurrentUser CustomUserDetails userDetails
    ) {
        // the file is encrypted in the browser; its key travels inside an E2E-encrypted chat message
        return Map.of("fileId", chatService.uploadChatFile(chatId, file, userDetails.user()));
    }

    @GetMapping("/rooms/{chatId}/files/{fileId}")
    ResponseEntity<Resource> downloadChatFile(
            @PathVariable String chatId,
            @PathVariable String fileId,
            @CurrentUser CustomUserDetails userDetails
    ) throws IOException {
        GridFsResource resource = chatService.getChatFile(chatId, fileId, userDetails.user());
        return ResponseEntity.ok()
                // ciphertext, but still keep it out of shared caches
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(resource.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().build().toString())
                .header("Content-Security-Policy", "sandbox")
                .body(new InputStreamResource(resource.getInputStream()));
    }
}