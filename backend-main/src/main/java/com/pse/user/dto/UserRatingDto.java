package com.pse.user.dto;


import com.pse.lecture.dto.response.LectureResponse;

//Used for "List of my Ratings
/**
 * Represents UserRatingDto.
 *
 * @param lecture the lecture
 * @param overallRating the overallRating
 */
public record UserRatingDto(

        LectureResponse lecture,
        double overallRating

) { }
