package aitu.network.aitunetwork.model.dto.chat;

import java.util.List;

/** Pushed to a message's sender (their ticks turn read) and to the reader's other sessions (badge clears). */
public record MessagesRead(String chatId, String readerId, List<String> messageIds) {
}
