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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
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

    /**
     * Participant public keys for every room the user is in, keyed by chatId, in one request instead of one
     * /keys call per chat. Rooms where a participant has no key yet are skipped; the client falls back to the
     * per-chat endpoint (which surfaces the 409) when it actually needs that room's keys.
     */
    public Map<String, Map<String, String>> getAllChatParticipantsPublicKeys(User user) {
        return chatRoomService.findRooms(user).stream()
                .filter(room -> room.getParticipants() != null
                        && room.getParticipants().stream().allMatch(participant -> participant.getPublicKey() != null))
                .collect(Collectors.toMap(
                        ChatRoom::getChatId,
                        room -> room.getParticipants().stream()
                                .collect(Collectors.toMap(User::getId, User::getPublicKey))
                ));
    }

    /** Unread count per chat for every room the user is in, keyed by chatId (badge counts in one request). */
    public Map<String, Long> getUnreadCounts(User user) {
        List<String> chatIds = chatRoomService.findRooms(user).stream().map(ChatRoom::getChatId).toList();
        return chatMessageService.countUnreadByChat(chatIds, user.getId());
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

    public List<ChatMessage> findChatMessages(String chatId, LocalDateTime before, int size) {
        return chatMessageService.findChatMessages(chatId, before, size);
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

    public List<ChatRoomsWithMessages> findChats(User user, Pageable pageable) {
        MatchOperation match = Aggregation.match(Criteria.where("participants.$id").in(new ObjectId(user.getId())));
        // Only the latest page of messages per room, not the whole history: a sub-pipeline $lookup that takes the
        // newest MESSAGE_PAGE_SIZE (sort desc + limit) and then re-sorts them ascending for display. Older messages
        // load on demand via the paginated /messages endpoint. Raw stage because the simple lookup can't limit.
        AggregationOperation lookup = context -> new Document("$lookup", new Document()
                .append("from", "chatMessage")
                .append("let", new Document("cid", "$" + ChatRoom.Fields.chatId))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr",
                                new Document("$eq", List.of("$" + ChatMessage.Fields.chatId, "$$cid")))),
                        new Document("$sort", new Document("createdAt", -1)),
                        new Document("$limit", ChatMessageService.MESSAGE_PAGE_SIZE),
                        new Document("$sort", new Document("createdAt", 1))
                ))
                .append("as", ChatRoomsWithMessages.Fields.messages));
        // Sort by the room's latest message so page 0 is the most recent conversations (the field the old sort
        // referenced never existed, so paging was effectively unordered). Rooms with no messages yet sort last.
        AddFieldsOperation addLastMessageAt = Aggregation.addFields()
                .addField("lastMessageAt")
                .withValueOf((AggregationExpression) context ->
                        new Document("$max", "$" + ChatRoomsWithMessages.Fields.messages + ".createdAt"))
                .build();
        SortOperation sort = Aggregation.sort(Sort.by(Sort.Direction.DESC, "lastMessageAt", "_id"));
        SkipOperation skip = Aggregation.skip(pageable.getOffset());
        LimitOperation limit = Aggregation.limit(pageable.getPageSize());
        ReplaceRootOperation rootReplacement = Aggregation.replaceRoot(
                context -> new Document(
                        "$mergeObjects", List.of(
                        new Document(ChatRoomsWithMessages.Fields.chatRoom, "$$ROOT"),
                        new Document(ChatRoomsWithMessages.Fields.messages, "$".concat(ChatRoomsWithMessages.Fields.messages))
                ))
        );

        Aggregation aggregation = Aggregation.newAggregation(match, lookup, addLastMessageAt, sort, skip, limit, rootReplacement);
        return mongoTemplate.aggregate(aggregation, ChatRoom.class, ChatRoomsWithMessages.class).getMappedResults();
    }
}
