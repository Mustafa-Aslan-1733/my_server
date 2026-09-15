package com.pse.social;

import com.pse.social.repository.AnswerVoteRepository;
import com.pse.social.repository.NotificationRepository;
import com.pse.social.service.AnswerCleanup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnswerCleanupTests {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AnswerVoteRepository answerVoteRepository;

    @InjectMocks
    private AnswerCleanup answerCleanup;


    @Test
    void clearDoesNothingWhenNoAnswerIdsAreGiven() {

        answerCleanup.clear(List.of());

        verifyNoInteractions(
                notificationRepository,
                answerVoteRepository
        );
    }


    @Test
    void clearDeletesNotificationsAndVotesForAnswers() {

        List<UUID> answerIds =
                List.of(
                        UUID.randomUUID(),
                        UUID.randomUUID()
                );


        answerCleanup.clear(answerIds);


        verify(notificationRepository).deleteByAnswerIdIn(answerIds);

        verify(answerVoteRepository).deleteByAnswerIdIn(answerIds);
    }


    @Test
    void clearDeletesNotificationsBeforeVotes() {

        List<UUID> answerIds =
                List.of(UUID.randomUUID());

        InOrder order =
                inOrder(
                        notificationRepository,
                        answerVoteRepository
                );


        answerCleanup.clear(answerIds);


        order.verify(notificationRepository).deleteByAnswerIdIn(answerIds);

        order.verify(answerVoteRepository).deleteByAnswerIdIn(answerIds);
    }
}