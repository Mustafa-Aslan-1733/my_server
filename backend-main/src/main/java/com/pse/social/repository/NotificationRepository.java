package com.pse.social.repository;


import com.pse.social.model.Notification;
import com.pse.user.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines NotificationRepository.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Returns findAllByRecipientAndSeenFalseOrderByCreatedAtDesc.
     *
     * @param recipient the recipient
     * @return the result
     */
    List<Notification> findAllByRecipientAndSeenFalseOrderByCreatedAtDesc(Student recipient);

    /**
     * Clears the notifications that point at these answers. An answer notification
     * dereferences its answer when it is read, so leaving one behind after the answer is
     * deleted breaks the student's notification list — and the foreign key.
     *
     * @param answerIds the answerIds
     * @return the result
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.answer.id IN :answerIds")
    int deleteByAnswerIdIn(@Param("answerIds") java.util.Collection<UUID> answerIds);


    /**
     * Returns markAllAsSeen.
     *
     * @param recipient the recipient
     * @return the result
     */
    @Modifying
    @Transactional
    @Query("""
    UPDATE Notification n
    SET n.seen = true
    WHERE n.recipient = :recipient
      AND n.seen = false
     """)
    int markAllAsSeen(@Param("recipient") Student recipient);


    /**
     * Returns findByRecipientAndId.
     *
     * @param recipient the recipient
     * @param id the id
     * @return the result
     */
    Optional<Notification> findByRecipientAndId(Student recipient, UUID id);
}
