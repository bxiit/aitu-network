package aitu.network.aitunetwork.config.security;

import aitu.network.aitunetwork.model.entity.User;

import java.security.Principal;

/**
 * The authenticated user of a STOMP session. Its name is the user id, so {@code convertAndSendToUser(userId, ...)}
 * routes to this session and clients can only subscribe to their own {@code /user/**} destinations.
 */
public record StompPrincipal(User user) implements Principal {
    @Override
    public String getName() {
        return user.getId();
    }
}
