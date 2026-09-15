package com.pse;

import com.pse.auth.repository.TokenRepository;
import com.pse.moderation.repository.AdminRepository;
import com.pse.support.AdminSessions;
import com.pse.support.DatabaseReset;
import com.pse.support.TestDeliveryConfig;
import com.pse.support.TestGitLabConfig;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transport-layer refusals, over a real servlet container.
 *
 * <p><b>Why this class exists at all, rather than four more cases in
 * {@code ApiProtocolContractTests}.</b> MockMvc is Spring's dispatcher without a server: there
 * is no Tomcat, no connector and no multipart parsing, so {@code MultipartException} and
 * {@code MaxUploadSizeExceededException} <em>cannot be raised</em> there. Probed before this
 * class was written: an oversized {@code multipart/form-data} body through MockMvc answers
 * <b>415</b>, from content negotiation, because the request never reaches a parser. A sweep
 * built on MockMvc would therefore have gone green against a broken handler and proved
 * nothing -- the failure mode this repository keeps writing down.
 *
 * <p>{@code F-17}'s writeup recorded these two as still falling to the catch-all and "still to
 * be measured separately". This is that measurement, and it needs a socket to make it.
 *
 * <p><b>The cost, stated because it is real.</b> {@code RANDOM_PORT} forks a second Spring
 * context in {@code server:test}: about one extra application start. The alternative was
 * leaving a production 500 unmeasured, or asserting it against a fixture that cannot produce
 * it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestDeliveryConfig.class, TestGitLabConfig.class, DatabaseReset.class})
class TransportLimitTests {

    private static final String ADMIN_EMAIL = "transport-admin@student.kit.edu";

    @LocalServerPort int port;

    @Autowired DatabaseReset databaseReset;
    @Autowired StudentRepository studentRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired TokenRepository tokenRepository;

    private RestClient http;
    private String token;

    @BeforeEach
    void startServerSession() {
        databaseReset.all();
        token = AdminSessions.createAdmin(
                ADMIN_EMAIL, studentRepository, adminRepository, tokenRepository);
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    /**
     * A multipart body past Boot's default 1 MB file limit. The request is well formed; the
     * server is refusing it for size, which is what 413 means.
     */
    @Test
    void aMultipartBodyOverTheSizeLimitIsRefusedWithTheDeclaredStatus() {
        int status = http.post()
                .uri("/admin/data/lectures")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=BOUNDARY"))
                .body(oversizedMultipartBody())
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value();

        assertThat(status)
                .as("an oversized multipart body reached the catch-all instead of a size refusal")
                .isEqualTo(413);
    }

    /**
     * The other half of what F-17 parked, **measured and found already correct**.
     *
     * <p>A malformed multipart envelope does not reach a parser on this application at all:
     * every route reads a JSON {@code @RequestBody}, so content negotiation rejects the media
     * type first and {@code handleUnsupportedMediaType} answers 415. That is the right answer
     * -- the route genuinely cannot read that media type -- and it is why no general
     * {@code MultipartException} handler was added beside the size one. There is no request
     * that produces the general case here, and a handler for a state nothing can reach is a
     * claim this suite cannot check.
     *
     * <p>Pinned so that adding a multipart endpoint later, which would change this answer,
     * has to be a deliberate act.
     */
    @Test
    void aMalformedMultipartEnvelopeIsRefusedByContentNegotiationBeforeAnyParser() {
        int status = http.post()
                .uri("/admin/data/lectures")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=BOUNDARY"))
                .body("this is not a multipart body")
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value();

        assertThat(status)
                .as("no route here reads multipart, so the media type is refused before parsing")
                .isEqualTo(415);
    }

    /**
     * The status tests above read only the code, so they would stay green if somebody replaced
     * the narrow handler with {@code ResponseEntityExceptionHandler}: the status would still be
     * 413 while the body silently became {@code application/problem+json}. The Android client
     * parses {@code {message, success}}. This is the same test F-17 added beside its sweep, for
     * the same reason.
     */
    @Test
    void theSizeRefusalCarriesTheProjectErrorBody() {
        String body = http.post()
                .uri("/admin/data/lectures")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=BOUNDARY"))
                .body(oversizedMultipartBody())
                .retrieve()
                .toEntity(String.class)
                .getBody();

        assertThat(body).contains("\"success\":false");
        assertThat(body).contains("Payload too large");
        assertThat(body).doesNotContain("problem+json");
        assertThat(body).doesNotContain("\"type\":");
    }

    private static String oversizedMultipartBody() {
        return "--BOUNDARY\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"big.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n"
                + "x".repeat(2 * 1024 * 1024)
                + "\r\n--BOUNDARY--\r\n";
    }
}
