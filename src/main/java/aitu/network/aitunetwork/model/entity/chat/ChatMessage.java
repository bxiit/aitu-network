package aitu.network.aitunetwork.model.entity.chat;

import aitu.network.aitunetwork.model.entity.BaseEntity;
import aitu.network.aitunetwork.model.enums.MessageStatus;
import aitu.network.aitunetwork.model.enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(of = "id", callSuper = false)
@Document(collection = "chatMessage")
@FieldNameConstants
public class ChatMessage extends BaseEntity implements Serializable {
    @Id
    private String id;
    @NotBlank(message = "errors.400.chats.id")
    private String chatId;
    @NotBlank(message = "errors.400.chats.senders.id")
    private String senderId;
    private Map<String, String> encryptedContent;
    private Map<String, String> encryptedKeys;
    private short length;
    // DELIVERED until someone else reads it, then RECEIVED (the sender's double tick)
    private MessageStatus status;
    // ids of the participants who have read it. Unread counts are per user, so a group member reading a
    // message doesn't clear it for the others. Messages saved before this field existed have none.
    private List<String> readBy;
    private MessageType type;
}