package com.pse.social.service;

import com.pse.social.repository.AnswerVoteRepository;
import com.pse.social.repository.NotificationRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * Clears the rows that reference an answer but are not cascaded from it.
 *
 * <p>{@code Answer} cascades only its reports. Answer votes and the notifications raised
 * for an answer are separate tables with their own foreign keys, so deleting an answer —
 * or anything that cascades down to one — has to clear them first. A notification also
 * dereferences its answer when the student reads it, so a leftover row breaks the
 * notification list even where the database would allow it.
 */
@Component
public class AnswerCleanup {

    private final NotificationRepository notificationRepository;
    private final AnswerVoteRepository answerVoteRepository;

    /**
     * Creates AnswerCleanup.
     *
     * @param notificationRepository the notificationRepository
     * @param answerVoteRepository the answerVoteRepository
     */
    public AnswerCleanup(
            NotificationRepository notificationRepository,
            AnswerVoteRepository answerVoteRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.answerVoteRepository = answerVoteRepository;
    }


    /**
     * Must run inside the transaction that performs the delete, before the delete.
     *
     * @param answerIds Ids of the answers
     */
    public void clear(Collection<UUID> answerIds) {
        if (answerIds.isEmpty()) {
            return;
        }
        notificationRepository.deleteByAnswerIdIn(answerIds);
        answerVoteRepository.deleteByAnswerIdIn(answerIds);
    }
}
