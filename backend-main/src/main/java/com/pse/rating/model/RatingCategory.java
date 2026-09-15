package com.pse.rating.model;

/**
 * Lists RatingCategory.
 */
public enum RatingCategory {

    /**
     * The OVERALL.
     */
    OVERALL("Overall", 0),

    // General
    /**
     * The ROOM.
     */
    ROOM("Room", 0.5),
    /**
     * The ORGANIZATION.
     */
    ORGANIZATION("Organization", 1.0),
    /**
     * The MATERIALS.
     */
    MATERIALS("Materials/Slides", 1.0),
    /**
     * The WORKLOAD.
     */
    WORKLOAD("Workload", 1.0),


    // Lecture
    /**
     * The LECTURE_UNDERSTANDABILITY.
     */
    LECTURE_UNDERSTANDABILITY("Understandability", 2.0),
    /**
     * The LECTURE_INTEREST.
     */
    LECTURE_INTEREST("Interest", 1.5),
    /**
     * The LECTURE_DIFFICULTY.
     */
    LECTURE_DIFFICULTY("Difficulty", 1.0),
    /**
     * The LECTURE_STRUCTURE.
     */
    LECTURE_STRUCTURE("Structure", 1.5),
    /**
     * The LECTURE_PACE.
     */
    LECTURE_PACE("Pace", 1.0),
    /**
     * The LECTURE_EXAMPLES.
     */
    LECTURE_EXAMPLES("Practical Examples", 1.0),


    // Professor
    /**
     * The PROFESSOR_ENGAGEMENT.
     */
    PROFESSOR_ENGAGEMENT("Engagement", 1.5),
    /**
     * The PROFESSOR_COMMUNICATION.
     */
    PROFESSOR_COMMUNICATION("Communication", 1.5),
    /**
     * The PROFESSOR_AVAILABILITY.
     */
    PROFESSOR_AVAILABILITY("Availability", 1.0),
    /**
     * The PROFESSOR_EXAM_PREPARATION.
     */
    PROFESSOR_EXAM_PREPARATION("Exam Preparation", 2.0),


    // Exercise
    /**
     * The EXERCISE_BOARDWORK.
     */
    EXERCISE_BOARDWORK("Board Work", 1.0),
    /**
     * The EXERCISE_QUESTIONS.
     */
    EXERCISE_QUESTIONS("Question Answering", 1.5),
    /**
     * The EXERCISE_HELPFULNESS.
     */
    EXERCISE_HELPFULNESS("Helpfulness", 2.0),
    /**
     * The EXERCISE_EXPLANATIONS.
     */
    EXERCISE_EXPLANATIONS("Explanations", 1.5),
    /**
     * The EXERCISE_EXAMPLES.
     */
    EXERCISE_EXAMPLES("Example Problems", 1.5),
    /**
     * The EXERCISE_PACE.
     */
    EXERCISE_PACE("Pace", 1.0),


    // Tutor
    /**
     * The TUTOR_EXPLANATION.
     */
    TUTOR_EXPLANATION("Explanation Skills", 2.0),
    /**
     * The TUTOR_PREPARATION.
     */
    TUTOR_PREPARATION("Preparation", 1.5),
    /**
     * The TUTOR_MOTIVATION.
     */
    TUTOR_MOTIVATION("Motivation", 1.0),
    /**
     * The TUTOR_FRIENDLINESS.
     */
    TUTOR_FRIENDLINESS("Friendliness", 1.0),
    /**
     * The TUTOR_FEEDBACK.
     */
    TUTOR_FEEDBACK("Feedback", 1.0);


    private final String displayName;
    private final double defaultWeight;

    RatingCategory(String displayName, double defaultWeight) {
        this.displayName = displayName;
        this.defaultWeight = defaultWeight;
    }

    /**
     * Returns getDisplayName.
     *
     * @return the result
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns getDefaultWeight.
     *
     * @return the result
     */
    public double getDefaultWeight() {
        return defaultWeight;
    }
}