package aitu.network.aitunetwork.service;


import aitu.network.aitunetwork.common.exception.ConflictException;
import aitu.network.aitunetwork.common.exception.EntityNotFoundException;
import aitu.network.aitunetwork.config.security.CustomUserDetails;
import aitu.network.aitunetwork.model.dto.UserShortDTO;
import aitu.network.aitunetwork.model.dto.UserUpdateDTO;
import aitu.network.aitunetwork.model.dto.chat.ChatRoomDTO;
import aitu.network.aitunetwork.model.dto.chat.StatusUpdate;
import aitu.network.aitunetwork.model.entity.Avatar;
import aitu.network.aitunetwork.model.entity.User;
import aitu.network.aitunetwork.model.entity.UserStatusDetails;
import aitu.network.aitunetwork.model.enums.AccessType;
import aitu.network.aitunetwork.model.enums.ConnectionStatus;
import aitu.network.aitunetwork.model.mapper.UserMapper;
import aitu.network.aitunetwork.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final FileService fileService;
    private final ChatRoomService chatRoomService;
    private final MongoTemplate mongoTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public void setProfilePhoto(MultipartFile file, User user) {
        String hexId = fileService.uploadFile(file);
        user.setAvatar(Avatar.builder()
                .id(hexId)
                .location(fileService.getLinkForResource(hexId))
                .build());
        userRepository.save(user);
    }

    public List<User> getUserFriends(String userId) {
        User user = getById(userId);
        return userRepository.findAllById(user.getFriendList());
    }

    public User getById(String idOrEmail) {
        return userRepository.findByIdOrEmail(idOrEmail)
                .orElseThrow(() -> new EntityNotFoundException(User.class, idOrEmail));
    }

    public User updateUser(UserUpdateDTO userDTO, User user) {
        user.setUsername(userDTO.username());
        user.setDescription(userDTO.description());
        user.setAccessType(userDTO.accessType());
        return userRepository.save(user);
    }

    public void deleteFriendById(String userId, User user) {
        if (CollectionUtils.isEmpty(user.getFriendList())) {
            throw new ConflictException("Friend List is empty");
        }
        boolean removed = user.getFriendList().removeIf(userId::equals);
        if (!removed) {
            throw new ConflictException("User doesnt have user with such id");
        }
        userRepository.save(user);
    }

    public List<User> save(Iterable<User> users) {
        return userRepository.saveAll(users);
    }

    public void deleteProfilePhoto(User user) {
        String pfpId = user.getAvatar().getId();
        user.setAvatar(null);
        userRepository.save(user);
        fileService.deleteFile(pfpId);
    }

    public Collection<UserShortDTO> getRelatedUsers(User user) {
        Map<String, UserShortDTO> contactedUsersMap = chatRoomService.findUserChatRooms(user).stream()
                .map(ChatRoomDTO::participants)
                .flatMap(List::stream)
                .filter(participant -> !participant.getId().equals(user.getId()))
                .collect(Collectors.toMap(UserShortDTO::getId, Function.identity(), (e, r) -> e));

        List<String> nonContactedFriendsIds = user.getFriendList().stream()
                .filter(userId -> !contactedUsersMap.containsKey(userId))
                .toList();

        return userRepository.findAllById(nonContactedFriendsIds).stream()
                .map(UserMapper::toShortDto)
                .collect(
                        () -> new LinkedHashSet<>(contactedUsersMap.values()),
                        HashSet::add,
                        AbstractCollection::addAll
                );
    }

    public List<User> searchUsers(String query, CustomUserDetails userDetails) {
        log.info("Query {}", query);
        return userRepository
                .findAllByEmailContainsIgnoreCaseOrUsernameContainsIgnoreCase(query, query)
                .stream()
                .filter(user -> userDetails == null || !user.getId().equals(userDetails.user().getId()))
                .collect(Collectors.toList());
    }

    public void connectUser(User user) {
        updateUserStatus(user, true, UserStatusDetails.Fields.connectedOn);
        messagingTemplate.convertAndSend("/topic/presence", new StatusUpdate(user.getId(), ConnectionStatus.ONLINE));
    }

    public void disconnectUser(User user) {
        updateUserStatus(user, false, UserStatusDetails.Fields.leftOn);
        messagingTemplate.convertAndSend("/topic/presence", new StatusUpdate(user.getId(), ConnectionStatus.OFFLINE));
    }

    private void updateUserStatus(User user, boolean connected, String connectedOrLeftOn) {
        Query query = new Query(Criteria.where("_id").is(user.getId()));
        Update update = new Update()
                .set(UserStatusDetails.Fields.connected, connected)
                .set(connectedOrLeftOn, Instant.now());

        mongoTemplate.updateFirst(query, update, User.class);
    }

    public Collection<User> fetchUsersByAccessType(AccessType accessType) {
        return userRepository.findByAccessType(accessType);
    }
}
