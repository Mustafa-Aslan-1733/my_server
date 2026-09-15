package com.pse.audit.service;

import com.pse.shared.error.ApiException;
import com.pse.shared.util.KeysetCursorCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A delegate over the shared codec whose only job is to bind the audit-specific error
 * message. What is worth testing is exactly that: that it forwards unchanged and that a
 * malformed audit cursor is reported as an audit cursor, not as somebody else's.
 */
@ExtendWith(MockitoExtension.class)
class AuditCursorCodecTests {

    private static final String AUDIT_MESSAGE = "Invalid audit cursor";
    private static final Instant CREATED_AT = Instant.parse("2026-08-20T15:44:39.123456Z");
    private static final UUID ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

    @Mock
    private KeysetCursorCodec delegate;

    @Test
    void encode_anyValues_forwardsToTheSharedCodec() {
        // Given
        when(delegate.encode(CREATED_AT, ID)).thenReturn("encoded-cursor");
        AuditCursorCodec codec = new AuditCursorCodec(delegate);

        // When
        String cursor = codec.encode(CREATED_AT, ID);

        // Then
        assertThat(cursor).isEqualTo("encoded-cursor");
        verify(delegate).encode(CREATED_AT, ID);
    }

    @Test
    void decode_anyValue_passesTheAuditErrorMessageToTheSharedCodec() {
        // Given
        KeysetCursorCodec.Cursor expected = new KeysetCursorCodec.Cursor(CREATED_AT, ID);
        when(delegate.decode("some-cursor", AUDIT_MESSAGE)).thenReturn(expected);
        AuditCursorCodec codec = new AuditCursorCodec(delegate);

        // When
        KeysetCursorCodec.Cursor decoded = codec.decode("some-cursor");

        // Then
        assertThat(decoded).isSameAs(expected);
        verify(delegate).decode("some-cursor", AUDIT_MESSAGE);
    }

    @Test
    void decode_sharedCodecRefuses_propagatesThatRefusalUnchanged() {
        // Given
        ApiException refusal = new ApiException(HttpStatus.BAD_REQUEST, AUDIT_MESSAGE);
        when(delegate.decode("bad", AUDIT_MESSAGE)).thenThrow(refusal);
        AuditCursorCodec codec = new AuditCursorCodec(delegate);

        // When / Then -- no wrapping, no re-messaging
        assertThatThrownBy(() -> codec.decode("bad")).isSameAs(refusal);
    }

    @Test
    void decode_cursorFromEncode_roundTripsOverTheRealSharedCodec() {
        // Given
        AuditCursorCodec codec = new AuditCursorCodec(new KeysetCursorCodec());

        // When
        KeysetCursorCodec.Cursor decoded = codec.decode(codec.encode(CREATED_AT, ID));

        // Then
        assertThat(decoded.createdAt()).isEqualTo(CREATED_AT);
        assertThat(decoded.id()).isEqualTo(ID);
    }

    @Test
    void decode_malformedCursor_reportsItAsAnInvalidAuditCursor() {
        // Given
        AuditCursorCodec codec = new AuditCursorCodec(new KeysetCursorCodec());

        // When / Then
        assertThatThrownBy(() -> codec.decode("!!!"))
                .isInstanceOf(ApiException.class)
                .hasMessage(AUDIT_MESSAGE);
    }
}
