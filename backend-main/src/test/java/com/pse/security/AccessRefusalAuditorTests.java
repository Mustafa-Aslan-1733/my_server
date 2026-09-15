package com.pse.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.auth.model.Token;
import com.pse.moderation.model.Admin;
import com.pse.user.model.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * What the moderation panel shows under "refused". Two properties carry the class and neither
 * had a test: an anonymous caller must produce **no** record — there is no account to attribute
 * the attempt to and anyone can repeat it — and a failure to write the record must not change
 * the answer the caller already earned.
 *
 * <p>The second one is the reason the try/catch is there, and the reason it is tested: turning
 * a failed audit write into a 500 would replace a correct 403 with a wrong answer, on the
 * request of somebody who was being refused anyway.
 */
@ExtendWith(MockitoExtension.class)
class AccessRefusalAuditorTests {

    @Mock private AuditWriter auditWriter;

    private ListAppender<ILoggingEvent> logs;
    private Logger auditorLogger;

    @BeforeEach
    void attachLogAppender() {
        logs = new ListAppender<>();
        logs.start();
        auditorLogger = (Logger) LoggerFactory.getLogger(AccessRefusalAuditor.class);
        auditorLogger.addAppender(logs);
    }

    @AfterEach
    void cleanUp() {
        auditorLogger.detachAppender(logs);
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/students");
        request.setRequestURI("/admin/students");
        return request;
    }

    private static AuthenticatedUser principal(Admin admin) {
        Student student = new Student();
        student.setUsername("student-1");
        return new AuthenticatedUser(student, new Token(), admin);
    }

    private static void authenticateAs(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void record_anonymousCaller_writesNothing() {
        // A 401 happens before any authorization rule, so there is no account to name and the
        // attempt says nothing about anybody -- recording it would fill the trail with noise
        // anyone on the internet can generate.
        new AccessRefusalAuditor(auditWriter).record(request());

        verifyNoInteractions(auditWriter);
    }

    @Test
    void record_principalThatIsNotOneOfOurs_writesNothing() {
        authenticateAs("anonymousUser");

        new AccessRefusalAuditor(auditWriter).record(request());

        verifyNoInteractions(auditWriter);
    }

    @Test
    void record_authenticatedStudent_namesTheMethodAndPathInTheTrail() {
        AuthenticatedUser principal = principal(null);
        authenticateAs(principal);

        new AccessRefusalAuditor(auditWriter).record(request());

        verify(auditWriter).writeRefusal(
                eq(principal.student()),
                eq(false),
                eq(AuditAction.ACCESS_REFUSED),
                eq(AuditTargetType.ENDPOINT),
                eq(null),
                eq("GET /admin/students"),
                eq(Map.of("method", "GET", "path", "/admin/students", "status", 403)));
    }

    @Test
    void record_administrator_isRecordedAsAnAdminRefusal() {
        // The actor type the entry is written under decides which tab of the panel it appears
        // in, so an administrator being refused has to arrive as an administrator.
        AuthenticatedUser principal = principal(new Admin());
        authenticateAs(principal);

        new AccessRefusalAuditor(auditWriter).record(request());

        verify(auditWriter).writeRefusal(
                any(), eq(true), any(), any(), any(), any(), any());
    }

    @Test
    void record_auditWriteFails_doesNotDisturbTheRefusalAndLeavesAWarning() {
        authenticateAs(principal(null));
        doThrow(new IllegalStateException("audit table is gone"))
                .when(auditWriter)
                .writeRefusal(any(), anyBoolean(), any(), any(), any(), any(), any());

        assertThatCode(() -> new AccessRefusalAuditor(auditWriter).record(request()))
                .doesNotThrowAnyException();

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("GET", "/admin/students");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }
}
