package com.pse.rating.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Provides RatingTopic.
 */
@Entity
@Table(
    name = "rating_topics",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"rating_id", "category"})
    }
)
@Getter
@Setter
@NoArgsConstructor
public class RatingTopic {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private RatingCategory category;

    @Column(nullable = false)
    private double value;

    @ManyToOne(optional = false)
    @JoinColumn(name = "rating_id")
    private Rating rating;
}
