package com.pse.social.model;


import com.pse.shared.enums.VoteType;
import com.pse.user.model.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Id;

import java.util.UUID;


/**
 * Provides CommentVote.
 */
@Entity
@Table(name = "comment_votes",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"student_id", "comment_id"}
        )
)

@Getter
@Setter
@NoArgsConstructor
public class CommentVote {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(optional = false)
    @JoinColumn(name = "comment_id")
    private Comment comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoteType vote;
}