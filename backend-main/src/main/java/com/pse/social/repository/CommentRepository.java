package com.pse.social.repository;



import com.pse.shared.enums.ContentStatus;
import com.pse.social.model.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


public interface CommentRepository extends JpaRepository<Comment, UUID> {


    /**
     * Newest first, matching findAllByOrderByCreatedAtDesc below. Both of the app-tier
     * listings used to carry no ORDER BY at all while the admin listing beside them did, so
     * the order the reader saw was the database's choice and free to change between requests.
     */
    List<Comment> findAllByLectureIdAndStatusOrderByCreatedAtDesc(
            UUID lectureId, ContentStatus status);

    @EntityGraph(attributePaths = {"student", "lecture"})
    List<Comment> findAllByStatusOrderByCreatedAtDesc(ContentStatus status);

    @EntityGraph(attributePaths = {"student", "lecture"})
    List<Comment> findAllByOrderByCreatedAtDesc();
}



