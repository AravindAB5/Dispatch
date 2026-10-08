package com.dispatch.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.dispatch.auth.CurrentUser.AuthException;
import com.dispatch.auth.dto.AuthUserResponse;
import com.dispatch.auth.dto.LoginRequest;
import com.dispatch.auth.dto.LoginResponse;
import com.dispatch.user.User;
import com.dispatch.user.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    /** Compared against when the email is unknown so response timing does not reveal which emails exist. */
    private final String timingDecoyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.timingDecoyHash = passwordEncoder.encode("dispatch-timing-decoy");
    }

    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            // No stored password can be longer than BCrypt's 72-byte limit.
            throw invalidCredentials();
        }
        Optional<User> candidate = userRepository.findByEmailIgnoreCase(request.email().trim());
        if (candidate.isEmpty()) {
            passwordEncoder.matches(request.password(), timingDecoyHash);
            throw invalidCredentials();
        }
        User user = candidate.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            throw new AuthException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "This account has been deactivated. Contact an administrator.");
        }
        TokenService.IssuedToken token = tokenService.issue(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), AuthUserResponse.from(user));
    }

    public AuthUserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .map(AuthUserResponse::from)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID",
                        "Your account is no longer active. Sign in again."));
    }

    private static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }
}
