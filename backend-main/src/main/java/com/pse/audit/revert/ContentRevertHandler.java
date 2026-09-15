package com.pse.audit.revert;

import com.pse.audit.model.AuditTargetType;
import com.pse.moderation.dto.request.ContentUpdateRequest;
import com.pse.moderation.service.ContentModerationService;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.enums.ContentStatus;
import com.pse.social.model.Answer;
import com.pse.social.model.Comment;
import com.pse.social.repository.AnswerRepository;
import com.pse.social.repository.CommentRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Undoes comment and answer edits. The two are separate target types but identical in shape,
 * so one class supplies both handlers rather than duplicating the mapping.
 */
public final class ContentRevertHandler {

    private ContentRevertHandler() {
    }

    /**
     * Provides CommentHandler.
     */
    @Component
    public static class CommentHandler implements AuditRevertHandler {

        private final CommentRepository commentRepository;
        private final ContentModerationService contentService;

        /**
         * Creates CommentHandler.
         *
         * @param commentRepository the commentRepository
         * @param contentService the contentService
         */
        public CommentHandler(
                CommentRepository commentRepository,
                ContentModerationService contentService
        ) {
            this.commentRepository = commentRepository;
            this.contentService = contentService;
        }

        @Override
        public AuditTargetType targetType() {
            return AuditTargetType.COMMENT;
        }

        @Override
        public Map<UUID, Map<String, Object>> currentValues(Collection<UUID> targetIds) {
            Map<UUID, Map<String, Object>> values = new HashMap<>();
            for (Comment comment : commentRepository.findAllById(targetIds)) {
                Map<String, Object> current = new LinkedHashMap<>();
                current.put("content", comment.getContent());
                current.put("status", comment.getStatus().name());
                values.put(comment.getId(), current);
            }
            return values;
        }

        @Override
        public void applyInverse(
                AuthenticatedUser principal,
                UUID targetId,
                Map<String, Object> before
        ) {
            contentService.updateComment(principal, targetId, new ContentUpdateRequest(
                    RevertValues.asString(before, "content"),
                    RevertValues.asEnum(before, "status", ContentStatus.class)
            ));
        }
    }

    /**
     * Provides AnswerHandler.
     */
    @Component
    public static class AnswerHandler implements AuditRevertHandler {

        private final AnswerRepository answerRepository;
        private final ContentModerationService contentService;

        /**
         * Creates AnswerHandler.
         *
         * @param answerRepository the answerRepository
         * @param contentService the contentService
         */
        public AnswerHandler(
                AnswerRepository answerRepository,
                ContentModerationService contentService
        ) {
            this.answerRepository = answerRepository;
            this.contentService = contentService;
        }

        @Override
        public AuditTargetType targetType() {
            return AuditTargetType.ANSWER;
        }

        @Override
        public Map<UUID, Map<String, Object>> currentValues(Collection<UUID> targetIds) {
            Map<UUID, Map<String, Object>> values = new HashMap<>();
            for (Answer answer : answerRepository.findAllById(targetIds)) {
                Map<String, Object> current = new LinkedHashMap<>();
                current.put("content", answer.getContent());
                current.put("status", answer.getStatus().name());
                values.put(answer.getId(), current);
            }
            return values;
        }

        @Override
        public void applyInverse(
                AuthenticatedUser principal,
                UUID targetId,
                Map<String, Object> before
        ) {
            contentService.updateAnswer(principal, targetId, new ContentUpdateRequest(
                    RevertValues.asString(before, "content"),
                    RevertValues.asEnum(before, "status", ContentStatus.class)
            ));
        }
    }
}
