package com.pse.audit.service;

import com.pse.shared.util.KeysetCursorCodec;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * The audit log's view of the shared {@link KeysetCursorCodec}: same format, audit-specific
 * error message. Kept as its own type so the audit service reads as if it owned its cursor.
 */
@Component
public class AuditCursorCodec {

    private static final String INVALID = "Invalid audit cursor";

    private final KeysetCursorCodec codec;

    /**
     * Creates AuditCursorCodec.
     *
     * @param codec the codec
     */
    public AuditCursorCodec(KeysetCursorCodec codec) {
        this.codec = codec;
    }

    /**
     * Returns encode.
     *
     * @param createdAt the createdAt
     * @param id the id
     * @return the result
     */
    public String encode(Instant createdAt, UUID id) {
        return codec.encode(createdAt, id);
    }

    /**
     * Returns decode.
     *
     * @param value the value
     * @return the result
     */
    public KeysetCursorCodec.Cursor decode(String value) {
        return codec.decode(value, INVALID);
    }
}
