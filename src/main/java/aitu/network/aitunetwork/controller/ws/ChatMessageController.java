package aitu.network.aitunetwork.controller.ws;

import aitu.network.aitunetwork.common.exception.UnauthorizedException;
import aitu.network.aitunetwork.config.security.CustomUserDetails;
import aitu.network.aitunetwork.model.dto.chat.MessageMark;
import aitu.network.aitunetwork.model.entity.User;
import aitu.network.aitunetwork.model.entity.chat.ChatMessage;
import aitu.network.aitunetwork.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {
    private final ChatService chatService;

    @MessageMapping("/chat")
    public void processMessage(
            @Payload @Valid ChatMessage newChatMessage,
            Principal principal
    ) {
        // the sender is whoever authenticated the WebSocket handshake (jwt cookie), not the payload's senderId
        newChatMessage.setSenderId(currentUser(principal).getId());
        chatService.processMessage(newChatMessage);
    }

    @MessageMapping("/chat/message/status")
    public void processMessageStatus(@Payload MessageMark messageMark) {
        chatService.processMessageStatus(messageMark);
    }

    private static User currentUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof CustomUserDetails(User user)) {
            return user;
        }
        throw new UnauthorizedException("User not authorized");
    }
}
