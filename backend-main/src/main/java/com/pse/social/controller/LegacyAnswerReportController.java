package com.pse.social.controller;

import com.pse.security.AuthenticatedUser;
import com.pse.shared.dto.BasicResponse;
import com.pse.social.dto.request.AnswerReportRequest;
import com.pse.social.service.ContentReportService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /answers/report}, the unprefixed path the Android client reports answers on.
 *
 * <p>Its own class rather than a second mapping beside {@link SocialController#submitAnswerReport}
 * because a method path is relative to the class prefix, and widening that class to
 * {@code &#123;"/social", ""&#125;} would alias {@code /comments}, {@code /answers} and
 * {@code /notifications} onto the moderation controllers that already map them. A controller
 * with no class-level prefix is the only shape that publishes this one path and nothing else,
 * and it is deleted in one file when the client has migrated.
 *
 * <p><b>Why it exists at all.</b> The client has always sent {@code /answers/report} while the
 * API has always served {@code /social/answers/report}: the path matched
 * {@code /answers/&#123;id&#125;} on {@code ModerationAnswerController}, which maps no POST, so
 * the request was answered 405. The client tells the user the report was submitted before the
 * response arrives, so the failure was invisible on both sides.
 *
 * <p>The guard lives in {@code SecurityConfig}, which matches {@code POST /answers/report} as
 * authenticated. Without that line the chain's closing {@code anyRequest().permitAll()} would
 * let an anonymous caller file reports -- the PATCH and DELETE matchers on
 * {@code /answers/**} do not cover POST.
 */
@RestController
public class LegacyAnswerReportController {

    private final ContentReportService contentReportService;

    /**
     * @param contentReportService the same service {@link SocialController} delegates to, so
     *                             the two paths cannot answer differently
     */
    public LegacyAnswerReportController(ContentReportService contentReportService) {
        this.contentReportService = contentReportService;
    }

    /**
     * Files a report against an answer.
     *
     * @param principal the reporting student's session
     * @param request   the answer's id, the reason and the reporter's explanation
     * @return the same body {@code POST /social/answers/report} returns
     */
    @PostMapping("/answers/report")
    public BasicResponse submitAnswerReport(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AnswerReportRequest request
    ) {
        return contentReportService.submitAnswerReport(principal.student(), request);
    }
}
