package com.dispatch.websocket;

import com.dispatch.auth.ActiveUserJwtConverter;
import com.dispatch.notification.RealtimeTopics;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Authenticates WebSocket sessions. Browsers cannot set headers on the WebSocket handshake, so the JWT is
 * sent in the STOMP CONNECT frame instead and validated here with the same decoder the REST API uses.
 * The channel is read-only for clients: they may subscribe to known topics but not publish.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Set<String> TOPICS = Set.of(RealtimeTopics.EVENTS, RealtimeTopics.INCIDENTS,
            RealtimeTopics.SERVICES, RealtimeTopics.DEVICES);

    private final JwtDecoder jwtDecoder;
    private final ActiveUserJwtConverter authenticationConverter;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder, ActiveUserJwtConverter authenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> {
                if (accessor.getUser() == null) {
                    throw new AccessDeniedException("Not authenticated");
                }
                if (!TOPICS.contains(accessor.getDestination())) {
                    throw new AccessDeniedException("Unknown destination " + accessor.getDestination());
                }
            }
            case SEND -> throw new AccessDeniedException("Clients cannot publish messages");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, heart-beats: nothing to check.
            }
        }
        return message;
    }

    private AbstractAuthenticationToken authenticate(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new BadCredentialsException("Missing bearer token in STOMP CONNECT headers");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7).trim());
            return authenticationConverter.convert(jwt);
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid or expired token", e);
        }
    }
}
