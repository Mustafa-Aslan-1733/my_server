package com.pse.social.dto.request;

import com.pse.shared.enums.ReportReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code explanation} is optional, for the reason {@link CommentReportRequest} gives. */
public record AnswerReportRequest(
        @NotBlank String answerID,
        @NotNull ReportReason reportReason,
        String explanation
) { }
