package com.dispatch.user;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.dispatch.common.error.BusinessRuleException;
import com.dispatch.common.error.ConflictException;
import com.dispatch.common.error.FieldValidationException;
import com.dispatch.common.error.NotFoundException;
import com.dispatch.user.dto.CreateUserRequest;
import com.dispatch.user.dto.UpdateUserRequest;
import com.dispatch.user.dto.UserResponse;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> list() {
        Map<Long, Long> assigned = userRepository.countActiveAssignedIncidents();
        return userRepository.findAllByOrderByNameAsc().stream()
                .map(user -> UserResponse.from(user, assigned.getOrDefault(user.getId(), 0L)))
                .toList();
    }

    public UserResponse get(Long id) {
        User user = require(id);
        return UserResponse.from(user, userRepository.countActiveAssignedIncidents().getOrDefault(id, 0L));
    }

    public User require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            // BCrypt only uses the first 72 bytes; multi-byte characters can exceed that below 72 characters.
            throw new FieldValidationException("password", "must be at most 72 bytes (fewer for non-ASCII characters)");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email().trim())) {
            throw new ConflictException("EMAIL_TAKEN", "A user with email " + request.email() + " already exists");
        }
        User user = new User(request.name().trim(), request.email(), passwordEncoder.encode(request.password()),
                request.role());
        return UserResponse.from(userRepository.save(user), 0);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long actorId) {
        User user = require(id);
        boolean losesAdmin = user.getRole() == Role.ADMIN
                && ((request.role() != null && request.role() != Role.ADMIN)
                    || Boolean.FALSE.equals(request.active()));
        if (losesAdmin && id.equals(actorId)) {
            throw new BusinessRuleException("SELF_LOCKOUT",
                    "You cannot remove your own administrator access or deactivate your own account");
        }
        if (losesAdmin && user.isActive() && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("LAST_ADMIN", "At least one active administrator is required");
        }
        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        return UserResponse.from(user, userRepository.countActiveAssignedIncidents().getOrDefault(id, 0L));
    }
}
