package aitu.network.aitunetwork.repository;

import aitu.network.aitunetwork.model.entity.chat.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    List<ChatMessage> findByChatIdOrderByCreatedAt(String chatId);
}