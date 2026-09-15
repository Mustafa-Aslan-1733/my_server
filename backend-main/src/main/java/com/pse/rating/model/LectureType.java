package com.pse.rating.model;

import java.util.List;

/**
 * Lists LectureType.
 */
public enum LectureType {


    /**
     * The LECTURE_ONLY.
     */
    LECTURE_ONLY(List.of(
            RatingCategory.ROOM,
            RatingCategory.ORGANIZATION,
            RatingCategory.MATERIALS,
            RatingCategory.WORKLOAD,

            RatingCategory.LECTURE_UNDERSTANDABILITY,
            RatingCategory.LECTURE_INTEREST,
            RatingCategory.LECTURE_DIFFICULTY,
            RatingCategory.LECTURE_STRUCTURE,
            RatingCategory.LECTURE_PACE,

            RatingCategory.PROFESSOR_ENGAGEMENT,
            RatingCategory.PROFESSOR_COMMUNICATION,
            RatingCategory.PROFESSOR_AVAILABILITY,
            RatingCategory.PROFESSOR_EXAM_PREPARATION
    )),

    /**
     * The LECTURE_AND_EXERCISE.
     */
    LECTURE_AND_EXERCISE(List.of(
            RatingCategory.ROOM,
            RatingCategory.ORGANIZATION,
            RatingCategory.MATERIALS,
            RatingCategory.WORKLOAD,

            RatingCategory.LECTURE_UNDERSTANDABILITY,
            RatingCategory.LECTURE_INTEREST,
            RatingCategory.LECTURE_DIFFICULTY,
            RatingCategory.LECTURE_STRUCTURE,
            RatingCategory.LECTURE_PACE,

            RatingCategory.PROFESSOR_ENGAGEMENT,
            RatingCategory.PROFESSOR_COMMUNICATION,
            RatingCategory.PROFESSOR_AVAILABILITY,
            RatingCategory.PROFESSOR_EXAM_PREPARATION,

            RatingCategory.EXERCISE_BOARDWORK,
            RatingCategory.EXERCISE_QUESTIONS,
            RatingCategory.EXERCISE_HELPFULNESS,
            RatingCategory.EXERCISE_EXPLANATIONS,
            RatingCategory.EXERCISE_EXAMPLES,

            RatingCategory.TUTOR_EXPLANATION,
            RatingCategory.TUTOR_PREPARATION,
            RatingCategory.TUTOR_FRIENDLINESS,
            RatingCategory.TUTOR_FEEDBACK
    ));


    private final List<RatingCategory> defaultCategories;

    LectureType(List<RatingCategory> defaultCategories) {
        this.defaultCategories = defaultCategories;
    }

    /**
     * Returns getDefaultCategories.
     *
     * @return the result
     */
    public List<RatingCategory> getDefaultCategories() {
        return defaultCategories;
    }


}
