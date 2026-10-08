package com.dispatch.seed;

import com.dispatch.user.Role;
import com.dispatch.user.User;
import com.dispatch.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator in environments without seed data, from
 * {@code DISPATCH_ADMIN_EMAIL} / {@code DISPATCH_ADMIN_PASSWORD}. Does nothing once any user exists.
 */
@Component
@Order(2)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${dispatch.bootstrap-admin.name:Administrator}") String name,
            @Value("${dispatch.bootstrap-admin.email:}") String email,
            @Value("${dispatch.bootstrap-admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank() || userRepository.count() > 0) {
            return;
        }
        if (password.length() < 12) {
            log.warn("DISPATCH_ADMIN_PASSWORD must be at least 12 characters; bootstrap admin not created");
            return;
        }
        userRepository.save(new User(name, email, passwordEncoder.encode(password), Role.ADMIN));
        log.info("Created bootstrap administrator {}", email);
    }
}
