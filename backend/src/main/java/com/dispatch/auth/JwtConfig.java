package com.dispatch.auth;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Stateless HS256 JWTs: Dispatch both issues and validates its own tokens, so a shared secret is
 * sufficient. Spring Security's resource-server support performs signature, expiry and issuer checks;
 * {@link ActiveUserJwtConverter} then maps the token to the user's current role.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);
    private static final int MIN_SECRET_BYTES = 32;
    static final String DEVELOPMENT_SECRET_PREFIX = "dispatch-local-development-secret";

    private final SecurityProperties properties;

    public JwtConfig(SecurityProperties properties) {
        this.properties = properties;
    }

    @Bean
    SecretKey jwtSigningKey() {
        String secret = properties.jwt().secret();
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "DISPATCH_JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        if (secret.startsWith(DEVELOPMENT_SECRET_PREFIX)) {
            log.warn("Using the built-in development JWT secret. Set DISPATCH_JWT_SECRET outside local development.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }
}
