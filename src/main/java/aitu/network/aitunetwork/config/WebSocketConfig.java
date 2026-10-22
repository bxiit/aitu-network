package aitu.network.aitunetwork.config;

import aitu.network.aitunetwork.config.security.CustomUserDetails;
import aitu.network.aitunetwork.config.security.StompPrincipal;
import aitu.network.aitunetwork.model.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String LEGACY_USER_QUEUE_PREFIX = "/user/queue/";
    private static final String USER_TOPIC_PREFIX = "/user/topic/";

    @Value("${secure-talk.stomp-broker.enabled}")
    private boolean brokerRelayEnabled;
    @Value("${secure-talk.stomp-broker.host}")
    private String brokerHost;
    @Value("${secure-talk.stomp-broker.port}")
    private int brokerPort;
    @Value("${secure-talk.stomp-broker.login}")
    private String brokerLogin;
    @Value("${secure-talk.stomp-broker.passcode}")
    private String brokerPasscode;
    @Value("${secure-talk.stomp-broker.virtual-host}")
    private String brokerVirtualHost;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broker destinations are relayed to RabbitMQ (rabbitmq_stomp), so every backend instance shares them;
        // the local profile uses the in-memory simple broker instead (no RabbitMQ, no Reactor Netty).
        // Per-session user destinations (convertAndSendToUser) and broadcasts (presence) both live under /topic:
        // a RabbitMQ /topic subscription is an exclusive auto-delete queue that goes away with the session, and a
        // send with no subscriber is dropped, whereas /queue/<name> would leave a durable queue behind per session.
        // /user is NOT a broker topic: it is only the user-destination prefix, resolved per session, so a client
        // can't subscribe to another user's /user/** channel.
        if (brokerRelayEnabled) {
            config.enableStompBrokerRelay("/topic")
                    .setRelayHost(brokerHost)
                    .setRelayPort(brokerPort)
                    .setClientLogin(brokerLogin)
                    .setClientPasscode(brokerPasscode)
                    .setSystemLogin(brokerLogin)
                    .setSystemPasscode(brokerPasscode)
                    .setVirtualHost(brokerVirtualHost)
                    // let any instance deliver to a user connected to another instance
                    .setUserDestinationBroadcast("/topic/unresolved-user")
                    .setUserRegistryBroadcast("/topic/user-registry");
        } else {
            config.enableSimpleBroker("/topic");
        }
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(new AuthHandshakeHandler())
                .withSockJS();
    }

    // Reject any STOMP CONNECT that isn't backed by an authenticated user (no valid jwt cookie on the handshake).
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand()) && accessor.getUser() == null) {
                    throw new MessagingException("Unauthenticated WebSocket connection rejected");
                }
                // Clients that still subscribe to /user/queue/** get the /user/topic/** channel the server sends to.
                String destination = accessor.getDestination();
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        && destination != null && destination.startsWith(LEGACY_USER_QUEUE_PREFIX)) {
                    accessor.setDestination(USER_TOPIC_PREFIX + destination.substring(LEGACY_USER_QUEUE_PREFIX.length()));
                }
                return message;
            }
        });
    }

    /** Binds the STOMP session to a {@link StompPrincipal} (name = user id) taken from the authenticated handshake. */
    private static class AuthHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (request.getPrincipal() instanceof Authentication authentication
                    && authentication.getPrincipal() instanceof CustomUserDetails(User user)) {
                return new StompPrincipal(user);
            }
            return null; // CONNECT is then rejected by the inbound interceptor
        }
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(new ObjectMapper());
        converter.setContentTypeResolver(resolver);
        messageConverters.add(converter);
        return false;
    }
}
