package com.dispatch.auth;

import java.util.List;

import com.dispatch.user.User;
import com.dispatch.user.UserRepository;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Turns a validated JWT into an authentication using the user's <em>current</em> record rather than the
 * claims frozen into the token: a deactivated account is rejected immediately and a role change applies
 * on the next request, instead of when the token expires. One primary-key lookup per request.
 * Used for both REST requests and STOMP CONNECT frames.
 */
@Component
public class ActiveUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public ActiveUserJwtConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException e) {
            throw new InvalidBearerTokenException("The access token has no valid subject");
        }
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidBearerTokenException("The account for this token is no longer active"));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                user.getEmail());
    }
}
