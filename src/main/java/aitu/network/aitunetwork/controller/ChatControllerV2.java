package aitu.network.aitunetwork.controller;

import aitu.network.aitunetwork.common.annotations.CurrentUser;
import aitu.network.aitunetwork.config.security.CustomUserDetails;
import aitu.network.aitunetwork.model.dto.chat.ChatRoomsWithMessages;
import aitu.network.aitunetwork.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/chats")
@RequiredArgsConstructor
public class ChatControllerV2 {

    private final ChatService chatService;
    private final UserFetcher userFetcher;

    // without the trailing slash too: the Next.js proxy redirects "/chats/" to "/chats"
    @GetMapping({"/{userId}", "/", ""})
    List<ChatRoomsWithMessages> findChatMessages(
            @PathVariable(required = false) String userId,
            @CurrentUser CustomUserDetails userDetails,
            @PageableDefault Pageable pageable
    ) {
        return chatService.findChats(userDetails.resolveUserId(userId, userFetcher), pageable);
    }
}