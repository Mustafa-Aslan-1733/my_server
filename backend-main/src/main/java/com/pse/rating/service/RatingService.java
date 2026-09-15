package com.pse.rating.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.service.LectureService;
import com.pse.rating.dto.response.RatingCategoriesResponse;
import com.pse.rating.dto.response.RatingCategoryResponse;
import com.pse.rating.dto.response.RatingsAverageResponse;
import com.pse.rating.model.RatingCategory;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pse.lecture.model.Lecture;
import com.pse.rating.model.Rating;
import com.pse.rating.model.RatingTopic;
import com.pse.user.model.Student;
import com.pse.rating.repository.RatingRepository;
import com.pse.rating.dto.request.RatingRequest;
import com.pse.rating.dto.request.RatingTopicRequest;
import com.pse.rating.dto.response.TopicAverageResponse;

/**
 * Provides RatingService.
 */
@Service
public class RatingService {

    private final LectureService lectureService;


    private final RatingRepository ratingRepository;
    private final AuditWriter auditWriter;

    /**
     * Creates RatingService.
     *
     * @param lectureService the lectureService
     * @param ratingRepository the ratingRepository
     * @param auditWriter the auditWriter
     */
    public RatingService(LectureService lectureService, RatingRepository ratingRepository, AuditWriter auditWriter) {
        this.lectureService = lectureService;
        this.ratingRepository = ratingRepository;
        this.auditWriter = auditWriter;
    }


    /**
     * Returns submitRating.
     *
     * @param student the student
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse submitRating(Student student, RatingRequest request) {

        Lecture lecture = lectureService.getById(request.lectureId());

        // The same answer getOwnRating and getRatingCategories give below, and the last route
        // in this module that did not: a lecture that does not exist is a 404, not a 200
        // reporting failure in its body.
        if (lecture == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Lecture not found");
        }

        //Check if User already has Rating
        Rating rating = ratingRepository.findByStudentIdAndLectureId(student.getId(), request.lectureId()).orElse(null);

        if (rating == null) {
            rating = new Rating();
            rating.setLecture(lecture);
            rating.setStudent(student);

        } else {
            //Overwrite existing rating topics
            rating.getTopics().clear();
            ratingRepository.flush();
        }


        for (RatingTopicRequest topicRequest : request.topics()) {

            // OVERALL shouldnt be set by client
            if (topicRequest.category() == RatingCategory.OVERALL) {
                continue;
            }

            RatingTopic topic = new RatingTopic();

            topic.setCategory(topicRequest.category());
            topic.setValue(topicRequest.value());
            topic.setRating(rating);

            rating.getTopics().add(topic);

        }

        //Add overall Rating
        double overallRating = RatingAverages.weightedScore(rating);
        RatingTopic overallTopic = new RatingTopic();
        overallTopic.setCategory(RatingCategory.OVERALL);
        overallTopic.setValue(overallRating);
        overallTopic.setRating(rating);

        rating.getTopics().add(overallTopic);
        ratingRepository.saveAndFlush(rating);

        // No professor average is written here any more. The read moved to
        // ProfessorResponseMapper, which recomputes it -- so this loop was an UPDATE per
        // professor per submitted rating for a column nothing reads back, and the stale value
        // it left behind was what made recomputing on read necessary in the first place.

        String code = lecture.getCode();
        auditWriter.writeStudentAction(
                student,
                AuditAction.RATING_SUBMITTED,
                AuditTargetType.RATING,
                rating.getId(),
                code == null || code.isBlank() ? lecture.getName() : code + " — " + lecture.getName(),
                Map.of(
                        "lectureId", lecture.getId(),
                        "topicCount", rating.getTopics().size()
                )
        );

        return new BasicResponse("Rating submitted successfully", true);

    }




    /**
     * Returns getRatings.
     *
     * @param lectureId the lectureId
     * @return the result
     */
    @Transactional(readOnly = true)
    public RatingsAverageResponse getRatings(UUID lectureId) {

        List<Rating> ratings = ratingRepository.findByLectureIdAndStudentStatus(lectureId, UserStatus.ACTIVE);

        // EnumMap, not HashMap: Enum.hashCode is the identity hash, so a HashMap's key order
        // is decided by where the JVM put the constants -- different between two runs of the
        // same process, and between two instances behind a load balancer. This read is public
        // and its list reached the client in whatever order that came out as. An EnumMap
        // iterates in declaration order, always. F-21's defect, one package over.
        Map<RatingCategory, Double> sums = new EnumMap<>(RatingCategory.class);
        Map<RatingCategory, Double> counts = new EnumMap<>(RatingCategory.class);

        for (Rating rating : ratings) {

            for (RatingTopic topic : rating.getTopics()) {

                RatingCategory category = topic.getCategory();
                sums.put(category, sums.getOrDefault(category, 0.0) + topic.getValue());
                counts.put(category, counts.getOrDefault(category, 0.0) + 1);
            }
        }

        List<TopicAverageResponse> result = new ArrayList<>();

        for (RatingCategory category : sums.keySet()) {

            double average = (double) sums.get(category) / counts.get(category);

            result.add(new TopicAverageResponse(category, average));
        }

        return new RatingsAverageResponse("Success, found " + result.size() + " ratings", true, result);

    }

    /**
     * Returns getOwnRating.
     *
     * @param student the student
     * @param lectureId the lectureId
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public RatingsAverageResponse getOwnRating(Student student, UUID lectureId) {

        Lecture lecture = lectureService.getById(lectureId);

        // The same answer getRatingCategories gives for an unknown id, two methods below:
        // a lecture that does not exist is a 404, not a successful response reporting it.
        if (lecture == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Lecture not found");
        }

        Rating rating = ratingRepository.findByStudentIdAndLectureId(student.getId(), lectureId).orElse(null);

        // An empty list, never null. "I have not rated this lecture" is the ordinary answer
        // for any lecture a student has not rated -- which is every lecture with no ratings --
        // and a null list field is a crash waiting for the first caller that iterates the
        // response without checking. StudentService.getUserRatings already answers its own
        // empty case this way, deliberately, so a caller can iterate either way.
        if (rating == null) {
            return new RatingsAverageResponse(
                    "Error, couldn't find rating for Lecture: " + lectureId, false, List.of());
        }

        // Sorted by category, which for an enum is declaration order. Rating.topics is a JPA
        // bag with no @OrderBy, so this list arrived in whatever order the database returned
        // and the field reshuffled between requests -- the same defect getRatings above was
        // given an EnumMap for (F-27), on the route beside it. One response field cannot have
        // two orders.
        List<TopicAverageResponse> topics = rating.getTopics().stream()
                .sorted(Comparator.comparing(RatingTopic::getCategory))
                .map(topic -> new TopicAverageResponse(topic.getCategory(), topic.getValue()))
                .toList();

        return new RatingsAverageResponse("Success, found " + topics.size() + " ratings", true, topics);
    }


    /**
     * Returns getRatingCategories.
     *
     * @param lectureId the lectureId
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    public RatingCategoriesResponse getRatingCategories(UUID lectureId) {

        Lecture lecture = lectureService.getById(lectureId);

        // F-15: getById answers null rather than throwing, and the loop below dereferences
        // it. Without this an unknown id was an NPE and the catch-all turned that into a
        // 500 -- on a route anonymous callers can reach with any random UUID.
        if (lecture == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Lecture not found");
        }

        List<RatingCategoryResponse> responses = new ArrayList<>();

        for (RatingCategory category : lecture.getLectureType().getDefaultCategories()) {

            responses.add(
                    new RatingCategoryResponse(
                            category,
                            category.getDisplayName(),
                            category.getDefaultWeight()
                    )
            );
        }

        return new RatingCategoriesResponse("Found " + responses.size() + " categories", true, responses);
    }


}
