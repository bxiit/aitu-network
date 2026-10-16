package aitu.network.aitunetwork.model.entity.chat;

import aitu.network.aitunetwork.model.entity.BaseEntity;
import aitu.network.aitunetwork.model.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(of = "id", callSuper = false)
@Document
// findChats matches participants by their DBRef id ({'participants.$id': ...})
@CompoundIndex(name = "participants_ref_idx", def = "{'participants.$id': 1}")
@FieldNameConstants
public class ChatRoom extends BaseEntity {
    @Id
    private String id;
    @Indexed(unique = true)
    private String chatId;
    private String title;
    @DBRef(lazy = true)
    private List<User> participants;
    private ChatRoomType chatRoomType;
    private Boolean empty;

    public boolean addParticipant(User participant) {
        return participants.add(participant);
    }

    public boolean removeParticipant(User participant) {
        return participants.remove(participant);
    }
}