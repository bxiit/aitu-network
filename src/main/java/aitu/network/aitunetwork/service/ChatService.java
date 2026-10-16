package aitu.network.aitunetwork.service;

import aitu.network.aitunetwork.common.exception.ConflictException;
import aitu.network.aitunetwork.common.exception.ForbiddenException;
import aitu.network.aitunetwork.model.dto.UserShortDTO;
import aitu.network.aitunetwork.model.dto.WSMessageType;
import aitu.network.aitunetwork.model.dto.chat.*;
import aitu.network.aitunetwork.model.entity.User;
import aitu.network.aitunetwork.model.entity.chat.ChatMessage;
import aitu.network.aitunetwork.model.entity.chat.ChatRoom;
import aitu.network.aitunetwork.model.entity.chat.ChatRoomType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;
    private final UserService userService;
    private final MongoTemplate mongoTemplate;
    private final FileService fileService;

    public void processMessage(
            ChatMessage newChatMessage
    ) {
        if (newChatMessage.getEncryptedContent() == null || newChatMessage.getEncryptedKeys() == null) {
            throw new IllegalArgumentException("Message must be encrypted");
        }
        ChatRoom chatRoom = chatRoomService.findChatRoom(newChatMessage.getChatId());
        User sender = userService.getById(newChatMessage.getSenderId());
        if (!chatRoom.getParticipants().contains(sender)) {
            throw new RuntimeException("User is not a participant of this chat");
        }
        ChatMessage savedMessage = chatMessageService.save(newChatMessage);

        if (chatRoom.getEmpty()) {
            chatRoom = chatRoomService.markChatRoom(chatRoom, false);
        }

        WSMessage wsMessage = new WSMessage(WSMessageType.CHAT_MESSAGE, savedMessage);
        chatMessageService.sendWSMessageToUsers(
                chatRoom.getParticipants().stream().map(User::getId).toList(),
                wsMessage
        );
    }

    public Map<String, String> getChatParticipantsPublicKeys(String chatId, String userId) {
        ChatRoom chatRoom = chatRoomService.findChatRoom(chatId);
        User user = userService.getById(userId);

        if (!chatRoom.getParticipants().contains(user)) {
            throw new RuntimeException("User is not a participant of this chat");
        }

        // accounts created before end-to-end encryption get a key at their next login
        if (chatRoom.getParticipants().stream().anyMatch(participant -> participant.getPublicKey() == null)) {
            throw new ConflictException("errors.409.chats.keys");
        }
        return chatRoom.getParticipants().stream()
                .collect(Collectors.toMap(
                        User::getId,
                        User::getPublicKey
                ));
    }


    public Map<String, Object> countNewMessages(String chatId, User user) {
        requireParticipant(chatRoomService.findChatRoom(chatId), user);
        return Map.of("count", chatMessageService.countNewMessages(chatId, user));
    }

    public List<ChatMessage> findChatMessages(String chatId) {
        return chatMessageService.findChatMessages(chatId);
    }

    public List<ChatRoomsWithMessages> findChats(User user) {
        MatchOperation match = Aggregation.match(Criteria.where("participants.$id").in(new ObjectId(user.getId())));
        LookupOperation lookup = Aggregation.lookup(
                "chatMessage",
                ChatRoom.Fields.chatId,
                ChatMessage.Fields.chatId,
                ChatRoomsWithMessages.Fields.messages
        );
        ReplaceRootOperation rootReplacement = Aggregation.replaceRoot(
                context -> new Document(
                        "$mergeObjects", List.of(
                        new Document(ChatRoomsWithMessages.Fields.chatRoom, "$$ROOT"),
                        new Document(ChatRoomsWithMessages.Fields.messages, "$".concat(ChatRoomsWithMessages.Fields.messages))
                ))
        );

        Aggregation aggregation = Aggregation.newAggregation(match, lookup, rootReplacement);
        return mongoTemplate.aggregate(aggregation, ChatRoom.class, ChatRoomsWithMessages.class).getMappedResults();
    }

    public List<ChatRoomDTO> findUserChatRooms(User user) {
        return chatRoomService.findUserChatRooms(user);
    }


    public ChatRoomDTO createChatRoom(NewChatRoomDTO chatRoom, User user) {
        ChatRoomDTO dto = chatRoomService.createChatRoom(chatRoom, user);
        if (dto.chatRoomType().equals(ChatRoomType.GROUP)) {
            WSMessage wsMessage = new WSMessage(WSMessageType.CHAT_ROOM, dto);
            chatMessageService.sendWSMessageToUsers(dto.participants().stream().map(UserShortDTO::getId).toList(), wsMessage);
        }
        return dto;
    }

    public void addParticipantToChatRoom(String chatRoomId, String participantId) {
        chatRoomService.actionParticipantToChatRoom(chatRoomId, participantId, ChatRoom::addParticipant);
    }

    public void deleteUserFromChatRoom(String chatRoomId, String participantId) {
        chatRoomService.actionParticipantToChatRoom(chatRoomId, participantId, ChatRoom::removeParticipant);
    }

    public String uploadChatFile(String chatId, MultipartFile file, User uploader) {
        requireParticipant(chatRoomService.findChatRoom(chatId), uploader);
        return fileService.uploadChatFile(file, chatId, uploader.getId());
    }

    public GridFsResource getChatFile(String chatId, String fileId, User user) {
        requireParticipant(chatRoomService.findChatRoom(chatId), user);
        return fileService.getChatFile(chatId, fileId);
    }

    private static void requireParticipant(ChatRoom chatRoom, User user) {
        if (!chatRoom.getParticipants().contains(user)) {
            throw new ForbiddenException("errors.403.chats.participant");
        }
    }

    public void markChatAsRead(String chatId, User reader) {
        requireParticipant(chatRoomService.findChatRoom(chatId), reader);
        List<ChatMessage> read = chatMessageService.markChatAsRead(chatId, reader.getId());
        if (read.isEmpty()) {
            return;
        }
        // each sender learns which of their messages were read (their ticks turn double)
        read.stream()
                .collect(Collectors.groupingBy(ChatMessage::getSenderId,
                        Collectors.mapping(ChatMessage::getId, Collectors.toList())))
                .forEach((senderId, messageIds) -> chatMessageService.sendWSMessageToUsers(List.of(senderId),
                        new WSMessage(WSMessageType.MESSAGES_READ, new MessagesRead(chatId, reader.getId(), messageIds))));
        // the reader's other tabs and devices clear their unread badge
        chatMessageService.sendWSMessageToUsers(List.of(reader.getId()),
                new WSMessage(WSMessageType.MESSAGES_READ,
                        new MessagesRead(chatId, reader.getId(), read.stream().map(ChatMessage::getId).toList())));
    }
}
