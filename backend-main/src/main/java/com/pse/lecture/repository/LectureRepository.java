package com.pse.lecture.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pse.lecture.model.Lecture;

public interface LectureRepository extends JpaRepository<Lecture, UUID> {


    /*
     * List, not Set. These queries carry an ORDER BY, and the order is what the catalogue
     * listing serves to the client -- but a Set declares no order, so the contract said the
     * opposite of what the query means. It worked only because Spring Data happens to
     * materialise a LinkedHashSet, which is a fact about the framework rather than a
     * guarantee anybody wrote down. F-21's shape at the type level.
     */
    List<Lecture> findByActiveTrueOrderByNameAsc();
    List<Lecture> findAllByOrderByNameAsc();
    Optional<Lecture> findByName(String name);
}