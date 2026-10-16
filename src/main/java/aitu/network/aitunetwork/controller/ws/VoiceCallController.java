package aitu.network.aitunetwork.controller.ws;

import aitu.network.aitunetwork.model.dto.chat.SignalMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class VoiceCallController {
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/signaling/{receiverId}")
    public void handleSignal(
            @DestinationVariable("receiverId") String receiverId,
            @Payload SignalMessage signalMessage,
            Principal principal
    ) {
        // the sender is the authenticated session, never what the client puts in the payload
        signalMessage.setSenderId(ChatMessageController.currentUser(principal).getId());
        messagingTemplate.convertAndSendToUser(
                receiverId,
                "/queue/signaling",
                signalMessage
        );
    }
}
