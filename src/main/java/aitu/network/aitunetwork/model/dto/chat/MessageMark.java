package aitu.network.aitunetwork.model.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** "I've read this chat": marks every message in it as read for the sender of the frame. */
@Data
public class MessageMark {
    @NotBlank(message = "errors.400.chats.id")
    private String chatId;
}
