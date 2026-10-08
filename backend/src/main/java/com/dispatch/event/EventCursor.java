package com.dispatch.event;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import com.dispatch.common.error.FieldValidationException;

/**
 * Position in the event stream, ordered by (occurredAt DESC, id DESC). Encoded as an opaque base64 token
 * so the format can change without breaking clients.
 */
public record EventCursor(Instant occurredAt, long id) {

    public static EventCursor after(Event event) {
        return new EventCursor(event.getOccurredAt(), event.getId());
    }

    public String encode() {
        String raw = occurredAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static EventCursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            return new EventCursor(Instant.parse(raw.substring(0, separator)),
                    Long.parseLong(raw.substring(separator + 1)));
        } catch (RuntimeException e) {
            throw new FieldValidationException("cursor", "is not a valid cursor");
        }
    }
}
