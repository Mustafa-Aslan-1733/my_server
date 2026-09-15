package com.pse.professor.service;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pse.professor.mapper.ProfessorResponseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.professor.dto.request.ProfessorAddRequest;
import com.pse.professor.dto.response.ProfessorDetailResponse;
import com.pse.professor.dto.response.ProfessorResponse;
import com.pse.professor.dto.response.ProfessorsResponse;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.error.ApiException;

@Service
public class ProfessorService {

    private final ProfessorRepository professorRepository;
    private final LectureRepository lectureRepository;
    private final AuditWriter auditWriter;



    public ProfessorService(ProfessorRepository professorRepository, LectureRepository lectureRepository, AuditWriter auditWriter) {
        this.professorRepository = professorRepository;
        this.lectureRepository = lectureRepository;
        this.auditWriter = auditWriter;
    }


    @Transactional(readOnly = true)
    public ProfessorsResponse getProfessors() {

        List<Professor> professors = professorRepository.findByActiveTrueOrderByLastNameAscFirstNameAsc();

        List<ProfessorResponse> responses = new ArrayList<>();

        for (Professor professor : professors) {
            responses.add(ProfessorResponseMapper.toResponse(professor));
        }

        return new ProfessorsResponse("Found " + responses.size() + " professors", true, responses);
    }


    @Transactional(readOnly = true)
    public ProfessorsResponse getAllProfessors() {

        List<Professor> professors = professorRepository.findAllByOrderByLastNameAscFirstNameAsc();

        List<ProfessorResponse> responses = new ArrayList<>();

        for (Professor professor : professors) {
            responses.add(ProfessorResponseMapper.toResponse(professor));
        }

        return new ProfessorsResponse("Found " + responses.size() + " professors", true, responses);
    }



    /**
     * An unknown id is a 404, for the reason {@code LectureService.getLecture} gives: this
     * read and that one were the last two answering {@code 200 {"success": false}} to a
     * question every other read in the catalogue answers with a status code.
     */
    @Transactional(readOnly = true)
    public ProfessorDetailResponse getProfessor(UUID professor_id) {

        Professor professor = professorRepository.findWithLecturesById(professor_id).orElse(null);
        if (professor == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Professor not found");
        }


        return new ProfessorDetailResponse(
                "Found professor with uuid: " + professor_id,
                true,
                ProfessorResponseMapper.toResponse(professor)
        );

    }


    @Transactional
    /**
     * Creating a professor is recorded like every other administrative mutation.
     *
     * <p>See {@code LectureService.addLecture} for why this takes the principal and why the
     * entry is a lifecycle event rather than a field diff.
     */
    public BasicResponse addProfessor(AuthenticatedUser principal, ProfessorAddRequest request) {

    // The other half of addLecture's rule. This side already STORED the trimmed name (see
    // below) but checked uniqueness on the raw one, so " Stefan" / "Kuehnlein " walked past
    // this 409 and created a second row holding exactly the name the first one holds.
    String firstName = request.firstName() == null ? null : request.firstName().trim();
    String lastName = request.lastName() == null ? null : request.lastName().trim();

    if (getProfessorID(firstName, lastName) != null) {
        throw new ApiException(
                HttpStatus.CONFLICT,
                "Professor already exists: "
                        + firstName
                        + " "
                        + lastName);
    }

    List<Lecture> lectures = new ArrayList<>();
        if (request.lectureIDs() != null) {
            for (UUID lectureId : request.lectureIDs()) {

                Lecture lecture = lectureRepository.findById(lectureId).orElse(null);

                if (lecture == null) {
                    throw new ApiException(
                            HttpStatus.NOT_FOUND,
                            "Couldn't find lecture with lectureID: " + lectureId);
                }

                lectures.add(lecture);
            }
        }

        Professor professor = new Professor();
        professor.setFirstName(firstName);
        professor.setLastName(lastName);

        for (Lecture lecture : lectures) {
            lecture.getProfessors().add(professor);
            professor.getLectures().add(lecture);
        }

        professorRepository.save(professor);

        Map<String, Object> changes = AuditWriter.lifecycle(false, true);
        auditWriter.write(
                principal.admin(),
                AuditAction.PROFESSOR_CREATED,
                AuditTargetType.PROFESSOR,
                professor.getId(),
                professor.getFirstName() + " " + professor.getLastName(),
                changes,
                Map.of("lectures", lectures.size())
        );

        return new BasicResponse("Professor added successfully", true);
    }


    public UUID getProfessorID(String firstName, String lastName) {
        Professor professor = professorRepository.findByFirstNameAndLastName(firstName, lastName).orElse(null);
        if(professor == null) {
            return null;
        }
        return professor.getId();
    }


}
