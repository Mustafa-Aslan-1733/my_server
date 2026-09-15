package com.pse.shared.util;

import com.pse.shared.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cursor carries the whole of keyset pagination's correctness: a value that decodes to
 * anything other than what was encoded silently skips or repeats rows instead of failing.
 * These are characterization tests -- they pin the wire format and every refusal, so that
 * changing either has to be deliberate.
 */
class KeysetCursorCodecTests {

    private static final String MESSAGE = "Invalid audit cursor";
    private static final Instant CREATED_AT = Instant.parse("2026-08-20T15:44:39.123456Z");
    private static final UUID ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

    private final KeysetCursorCodec codec = new KeysetCursorCodec();

    private static String cursorOf(String payload) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void decode_encodedCursor_returnsTheOriginalValues() {
        // Given
        String cursor = codec.encode(CREATED_AT, ID);

        // When
        KeysetCursorCodec.Cursor decoded = codec.decode(cursor, MESSAGE);

        // Then
        assertThat(decoded.createdAt()).isEqualTo(CREATED_AT);
        assertThat(decoded.id()).isEqualTo(ID);
    }

    /**
     * Truncating to milliseconds here would make two rows written in the same millisecond
     * indistinguishable, and the page boundary would fall between them.
     */
    @Test
    void decode_instantWithNanosecondPrecision_keepsEveryDigit() {
        // Given
        Instant precise = Instant.parse("2026-08-20T15:44:39.123456789Z");

        // When
        KeysetCursorCodec.Cursor decoded = codec.decode(codec.encode(precise, ID), MESSAGE);

        // Then
        assertThat(decoded.createdAt()).isEqualTo(precise);
        assertThat(decoded.createdAt().getNano()).isEqualTo(123456789);
    }

    @Test
    void encode_anyValues_producesUrlSafeUnpaddedBase64() {
        // When
        String cursor = codec.encode(CREATED_AT, ID);

        // Then -- it travels as a query parameter, so no '+', '/' or '=' may appear
        assertThat(cursor).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void encode_knownValues_producesVersionedPipeDelimitedPayload() {
        // When
        String cursor = codec.encode(CREATED_AT, ID);

        // Then
        String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        assertThat(payload).isEqualTo("1|2026-08-20T15:44:39.123456Z|" + ID);
    }

    @Test
    void encode_localDateTime_matchesTheUtcInstantOverload() {
        // Given -- entities that store a LocalDateTime hold it as UTC
        LocalDateTime local = LocalDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC);

        // When
        String fromLocal = codec.encode(local, ID);

        // Then
        assertThat(fromLocal).isEqualTo(codec.encode(CREATED_AT, ID));
    }

    @Test
    void createdAtLocal_cursorEncodedFromLocalDateTime_returnsThatLocalDateTime() {
        // Given
        LocalDateTime local = LocalDateTime.of(2026, 8, 20, 15, 44, 39, 123456000);

        // When
        KeysetCursorCodec.Cursor decoded = codec.decode(codec.encode(local, ID), MESSAGE);

        // Then -- the round trip through Instant has to be lossless in both directions
        assertThat(decoded.createdAtLocal()).isEqualTo(local);
    }

    @Test
    void decode_null_throwsBadRequest() {
        // When / Then
        assertThatThrownBy(() -> codec.decode(null, MESSAGE))
                .isInstanceOf(ApiException.class)
                .hasMessage(MESSAGE)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .extracting(ApiException::getStatus)
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void decode_blankCursor_throwsBadRequest(String blank) {
        // When / Then
        assertThatThrownBy(() -> codec.decode(blank, MESSAGE))
                .isInstanceOf(ApiException.class)
                .hasMessage(MESSAGE);
    }

    /**
     * The cap exists so a hostile caller cannot make the service base64-decode an arbitrarily
     * large string.
     */
    @Test
    void decode_cursorLongerThanTheSizeCap_throwsBadRequest() {
        // Given
        String tooLong = "A".repeat(513);

        // When / Then
        assertThatThrownBy(() -> codec.decode(tooLong, MESSAGE))
                .isInstanceOf(ApiException.class)
                .hasMessage(MESSAGE);
    }

    @Test
    void decode_cursorAtExactlyTheSizeCap_isRejectedOnContentRatherThanLength() {
        // Given -- 512 valid base64 characters that decode to something that is not a payload
        String atCap = "A".repeat(512);

        // When / Then -- it clears the length gate and fails on the payload shape instead
        assertThat(atCap).hasSize(512);
        assertThatThrownBy(() -> codec.decode(atCap, MESSAGE))
                .isInstanceOf(ApiException.class)
                .hasMessage(MESSAGE);
    }

    @Test
    void decode_valueThatIsNotBase64_throwsBadRequest() {
        // When / Then
        assertThatThrownBy(() -> codec.decode("!!! not base64 !!!", MESSAGE))
                .isInstanceOf(ApiException.class)
                .hasMessage(MESSAGE);
    }

    /**
     * A hand-built cursor is both the realistic attack and the realistic client bug. Every
     * shape has to come back as a 400 -- in particular the unparseable timestamp, whose
     * DateTimeParseException is not an IllegalArgumentException and would otherwise escape
     * as a 500.
     */
    @ParameterizedTest
    @CsvSource({
            "2|2026-08-20T15:44:39.123456Z|3f2504e0-4f89-11d3-9a0c-0305e82c3301",
            "1|2026-08-20T15:44:39.123456Z",
            "1|2026-08-20T15:44:39.123456Z|3f2504e0-4f89-11d3-9a0c-0305e82c3301|extra",
            "1|not-a-date|3f2504e0-4f89-11d3-9a0c-0305e82c3301",
            "1|2026-08-20T15:44:39.123456Z|not-a-uuid",
            "1-2026-08-20T15:44:39.123456Z-3f2504e0-4f89-11d3-9a0c-0305e82c3301",
            "|||",
            "1||"
    })
    void decode_handBuiltMalformedPayload_throwsBadRequest(String payload) {
        // Given
        String cursor = cursorOf(payload);

        // When / Then
        assertThatThrownBy(() -> codec.decode(cursor, MESSAGE))
                .isInstanceOf(ApiException.class)
                .hasMessage(MESSAGE);
    }

    /** Each listing names its own parameter, so a caller can tell which cursor it got wrong. */
    @Test
    void decode_invalidCursor_reportsTheCallerSuppliedMessage() {
        // When / Then
        assertThatThrownBy(() -> codec.decode("!!!", "Invalid user cursor"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid user cursor");
    }

    @Test
    void encode_nullInstant_producesACursorThatCannotBeDecoded() {
        // Given -- encode does not validate, so the failure only surfaces on the way back in
        String cursor = codec.encode((Instant) null, ID);

        // When / Then
        assertThat(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8))
                .isEqualTo("1|null|" + ID);
        assertThatThrownBy(() -> codec.decode(cursor, MESSAGE))
                .isInstanceOf(ApiException.class)
                .hasMessage(MESSAGE);
    }

    @Test
    void encode_nullLocalDateTime_throwsNullPointerException() {
        // When / Then -- the overload dereferences it to convert to an Instant
        assertThatThrownBy(() -> codec.encode((LocalDateTime) null, ID))
                .isInstanceOf(NullPointerException.class);
    }
}
