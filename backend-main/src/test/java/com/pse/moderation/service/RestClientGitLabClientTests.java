package com.pse.moderation.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pse.config.properties.GitLabProperties;
import com.pse.shared.error.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/**
 * The last adapter the test plan listed as debt rather than as a decision: the whole class is
 * an HTTP conversation, and every test so far substituted {@code RecordingGitLabClient}
 * instead, so nothing stated what the real one sends or how it behaves when the tracker
 * answers something unexpected.
 *
 * <p>The conversation is driven by {@code MockRestServiceServer} rather than by mocking the
 * fluent {@code RestClient} chain: the chain here is six calls deep, and a mock of it would
 * pin the shape of the call instead of its result. The mock server produces real status codes,
 * so the failure arms close the way they would in production — and nothing touches the network.
 *
 * <p>The property this class exists to protect is that the token never leaves the backend, so
 * it gets its own tests: the header carries it, and neither the log line nor the message the
 * caller receives ever does.
 */
class RestClientGitLabClientTests {

    private static final String BASE_URL = "https://gitlab.example.com";
    private static final String PROJECT_ID = "42";
    private static final String TOKEN = "glpat-secret-token";
    private static final String ISSUES_URL = BASE_URL + "/api/v4/projects/42/issues";

    private RestClient.Builder builder;
    private MockRestServiceServer gitLab;
    private ListAppender<ILoggingEvent> logs;
    private Logger clientLogger;

    @BeforeEach
    void bindTheMockServer() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        gitLab = MockRestServiceServer.bindTo(builder).build();
        logs = new ListAppender<>();
        logs.start();
        clientLogger = (Logger) LoggerFactory.getLogger(RestClientGitLabClient.class);
        clientLogger.addAppender(logs);
    }

    @AfterEach
    void detachTheAppender() {
        clientLogger.detachAppender(logs);
    }

    private RestClientGitLabClient client() {
        return client(PROJECT_ID, TOKEN);
    }

    private RestClientGitLabClient client(String projectId, String token) {
        return new RestClientGitLabClient(builder.build(), projectId, token);
    }

    /** The wiring the application actually uses, so the constructor's own branches are real. */
    private static RestClientGitLabClient configuredClient(
            String baseUrl, String projectId, String token) {
        return new RestClientGitLabClient(
                new GitLabProperties(baseUrl, projectId, token, Duration.ofSeconds(10)));
    }

    // ---------------------------------------------------------------- isEnabled

    @Test
    void isEnabled_baseUrlProjectAndTokenAllPresent_isTrue() {
        assertThat(configuredClient(BASE_URL, PROJECT_ID, TOKEN).isEnabled()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "///"})
    void isEnabled_noUsableBaseUrl_isFalse(String baseUrl) {
        // "///" belongs here rather than with the happy path: the trailing-slash trim runs
        // before the emptiness check, so a base URL of nothing but slashes disables the
        // integration instead of producing a client pointed at "/".
        assertThat(configuredClient(baseUrl, PROJECT_ID, TOKEN).isEnabled()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void isEnabled_noProjectId_isFalse(String projectId) {
        assertThat(configuredClient(BASE_URL, projectId, TOKEN).isEnabled()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void isEnabled_noToken_isFalse(String token) {
        assertThat(configuredClient(BASE_URL, PROJECT_ID, token).isEnabled()).isFalse();
    }

    // ---------------------------------------------------------------- not configured

    @Test
    void createIssue_notConfigured_refusesWith503WithoutCallingGitLab() {
        // No expectation is registered, so the mock server fails the test on any request at
        // all -- which is what makes this an assertion that nothing was sent, not just that
        // an exception was thrown.
        assertThatThrownBy(() -> client(PROJECT_ID, "").createIssue("title", "body"))
                .isInstanceOf(ApiException.class)
                .hasMessage("GitLab integration is not configured")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        gitLab.verify();
    }

    @Test
    void createIssue_noBaseUrlConfigured_refusesWith503() {
        assertThatThrownBy(() -> configuredClient("", PROJECT_ID, TOKEN).createIssue("t", "b"))
                .isInstanceOf(ApiException.class)
                .hasMessage("GitLab integration is not configured")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    // ---------------------------------------------------------------- the request

    @Test
    void createIssue_configured_postsTheTitleAndDescriptionWithThePrivateTokenHeader() {
        gitLab.expect(requestTo(ISSUES_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PRIVATE-TOKEN", TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").value("Broken login"))
                .andExpect(jsonPath("$.description").value("Steps to reproduce"))
                .andRespond(withSuccess(
                        "{\"web_url\":\"https://gitlab.example.com/issues/7\",\"iid\":7}",
                        MediaType.APPLICATION_JSON));

        GitLabClient.CreatedIssue issue =
                client().createIssue("Broken login", "Steps to reproduce");

        assertThat(issue.url()).isEqualTo("https://gitlab.example.com/issues/7");
        assertThat(issue.iid()).isEqualTo(7);
        gitLab.verify();
    }

    @Test
    void createIssue_projectIdAndTokenWithSurroundingWhitespace_areTrimmedBeforeUse() {
        gitLab.expect(requestTo(ISSUES_URL))
                .andExpect(header("PRIVATE-TOKEN", TOKEN))
                .andRespond(withSuccess(
                        "{\"web_url\":\"https://gitlab.example.com/issues/1\",\"iid\":1}",
                        MediaType.APPLICATION_JSON));

        client("  " + PROJECT_ID + "  ", "  " + TOKEN + "  ").createIssue("t", "b");

        gitLab.verify();
    }

    @Test
    void createIssue_projectIdWithASlash_encodesItIntoASinglePathSegment() {
        // GitLab addresses a project either by number or by "group/project". Left unencoded
        // the slash splits the path and the issue is opened against a different project --
        // or against nothing at all.
        gitLab.expect(requestTo(BASE_URL + "/api/v4/projects/group%2Fproject/issues"))
                .andRespond(withSuccess(
                        "{\"web_url\":\"https://gitlab.example.com/issues/3\",\"iid\":3}",
                        MediaType.APPLICATION_JSON));

        client("group/project", TOKEN).createIssue("t", "b");

        gitLab.verify();
    }

    // ---------------------------------------------------------------- the answer

    @Test
    void createIssue_emptyResponseBody_answersBadGateway() {
        gitLab.expect(requestTo(ISSUES_URL)).andRespond(withSuccess());

        assertThatThrownBy(() -> client().createIssue("t", "b"))
                .isInstanceOf(ApiException.class)
                .hasMessage("GitLab did not return an issue")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void createIssue_responseWithoutAWebUrl_answersBadGateway() {
        gitLab.expect(requestTo(ISSUES_URL))
                .andRespond(withSuccess("{\"iid\":7}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client().createIssue("t", "b"))
                .isInstanceOf(ApiException.class)
                .hasMessage("GitLab did not return an issue");
    }

    @Test
    void createIssue_responseWithoutAnIid_returnsTheUrlWithNoIssueNumber() {
        gitLab.expect(requestTo(ISSUES_URL))
                .andRespond(withSuccess(
                        "{\"web_url\":\"https://gitlab.example.com/issues/7\"}",
                        MediaType.APPLICATION_JSON));

        GitLabClient.CreatedIssue issue = client().createIssue("t", "b");

        assertThat(issue.url()).isEqualTo("https://gitlab.example.com/issues/7");
        assertThat(issue.iid()).isNull();
    }

    @Test
    void createIssue_iidThatIsNotANumber_returnsTheUrlWithNoIssueNumber() {
        // The number a human quotes is optional; the link is not. A tracker that answers with
        // a string here must not take the whole submission down with a ClassCastException.
        gitLab.expect(requestTo(ISSUES_URL))
                .andRespond(withSuccess(
                        "{\"web_url\":\"https://gitlab.example.com/issues/7\",\"iid\":\"seven\"}",
                        MediaType.APPLICATION_JSON));

        GitLabClient.CreatedIssue issue = client().createIssue("t", "b");

        assertThat(issue.url()).isEqualTo("https://gitlab.example.com/issues/7");
        assertThat(issue.iid()).isNull();
    }

    // ---------------------------------------------------------------- failures

    @Test
    void createIssue_gitLabAnswersServerError_answersBadGatewayWithoutQuotingTheTracker() {
        gitLab.expect(requestTo(ISSUES_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client().createIssue("t", "b"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Could not reach GitLab")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void createIssue_tokenRejected_isIndistinguishableFromTheTrackerBeingDown() {
        // Characterization, and a deliberate one: a revoked token and an unreachable tracker
        // give the administrator the same 502. The distinction lives only in the log line
        // below, which is why that line is tested.
        gitLab.expect(requestTo(ISSUES_URL)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client().createIssue("t", "b"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Could not reach GitLab");

        assertThat(logs.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("HttpClientErrorException");
                });
    }

    @Test
    void createIssue_trackerUnreachable_answersBadGateway() {
        gitLab.expect(requestTo(ISSUES_URL))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> client().createIssue("t", "b"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Could not reach GitLab");
    }

    @Test
    void createIssue_failureCarryingTheTokenInItsMessage_neverLetsItReachTheLogOrTheCaller() {
        // The point of the class: the token is a credential, and the failure path is the one
        // place it could escape -- a client library that quotes the failing request would put
        // it in the message. The code logs the exception class only; this pins that.
        gitLab.expect(requestTo(ISSUES_URL))
                .andRespond(withException(
                        new IOException("POST failed, PRIVATE-TOKEN: " + TOKEN)));

        assertThatThrownBy(() -> client().createIssue("t", "b"))
                .isInstanceOf(ApiException.class)
                .hasMessageNotContaining(TOKEN);

        assertThat(logs.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage()).doesNotContain(TOKEN);
                    assertThat(event.getThrowableProxy()).isNull();
                });
    }

    @Test
    void createIssue_ownRefusal_isNotRewrittenByTheCatchAll() {
        // "GitLab did not return an issue" is thrown from inside the try block. Without the
        // ApiException arm ahead of the RuntimeException arm it would be swallowed and
        // reported as an unreachable tracker, and the cause would be lost.
        gitLab.expect(requestTo(ISSUES_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client().createIssue("t", "b"))
                .isInstanceOf(ApiException.class)
                .hasMessage("GitLab did not return an issue");

        assertThat(logs.list).isEmpty();
    }
}
