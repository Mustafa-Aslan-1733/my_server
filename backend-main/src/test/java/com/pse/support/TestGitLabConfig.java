package com.pse.support;

import com.pse.moderation.service.GitLabClient;
import com.pse.shared.error.ApiException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Substitutes the real client so the suite never depends on a tracker being reachable, and
 * so no test can leak a credential by accident. Counts calls, because idempotency is the
 * property that matters here and "did not call GitLab twice" is the only way to assert it.
 */
@TestConfiguration
public class TestGitLabConfig {

    @Bean
    @Primary
    RecordingGitLabClient recordingGitLabClient() {
        return new RecordingGitLabClient();
    }

    public static final class RecordingGitLabClient implements GitLabClient {

        private final AtomicBoolean enabled = new AtomicBoolean(false);
        private final AtomicBoolean failNext = new AtomicBoolean(false);
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public boolean isEnabled() {
            return enabled.get();
        }

        @Override
        public CreatedIssue createIssue(String title, String description) {
            calls.incrementAndGet();
            if (failNext.compareAndSet(true, false)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach GitLab");
            }
            return new CreatedIssue("https://gitlab.example/issues/" + calls.get(), calls.get());
        }

        public void enable() {
            enabled.set(true);
        }

        public void disable() {
            enabled.set(false);
        }

        public void failNext() {
            failNext.set(true);
        }

        public int calls() {
            return calls.get();
        }

        public void reset() {
            enabled.set(false);
            failNext.set(false);
            calls.set(0);
        }
    }
}
