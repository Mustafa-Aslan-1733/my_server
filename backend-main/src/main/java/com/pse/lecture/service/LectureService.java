package com.pse.lecture.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.pse.lecture.mapper.LectureResponseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.shared.dto.BasicResponse;
import com.pse.lecture.dto.request.AddLectureRequest;
import com.pse.lecture.dto.response.LectureDetailResponse;
import com.pse.lecture.dto.response.LectureResponse;
import com.pse.lecture.dto.response.LecturesResponse;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.shared.error.ApiException;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.security.AuthenticatedUser;


/**
 * Service for Lectures.
 *
 */
@Service
public class LectureService {


    private final LectureRepository lectureRepository;
    private final ProfessorRepository professorRepository;
    private final AuditWriter auditWriter;

    /**
     * Creates LectureService.
     *
     * @param lectureRepository the lectureRepository
     * @param professorRepository the professorRepository
     * @param auditWriter the auditWriter
     */
    public LectureService(LectureRepository lectureRepository, ProfessorRepository professorRepository, AuditWriter auditWriter) {
        this.lectureRepository = lectureRepository;
        this.professorRepository = professorRepository;
        this.auditWriter = auditWriter;
    }


    /**
     * Searches only for Lectures which are active.
     *
     * @return all active Lectures.
     */
    @Transactional(readOnly = true)
    public LecturesResponse getLectures() {

        return getLectures(false);
    }


    /**
     * Used by Admin Panel.
     * Searches for all Lectures (including non-active Lectures)
     *
     * @return all Lectures
     */
    @Transactional(readOnly = true)
    public LecturesResponse getAllLectures() {

        return getLectures(true);
    }

    /**
     * Retrieves a Lecture with a given ID.
     *
     * <p>An unknown id is a 404, the answer {@code RatingService} gives for the same question
     * two routes away. It used to be {@code 200 {"success": false, "lecture": null}} -- F-5's
     * direction applied to the last read that had not moved. The error body is
     * {@code ApiErrorResponse}, so {@code lecture} is absent rather than null; that is the
     * client-visible half and it carries a CHANGELOG entry.
     *
     * @param lectureId UUID
     * @return LectureDetailResponse
     *
     * @throws ApiException if the operation fails
     */
    @Transactional(readOnly = true)
    public LectureDetailResponse getLecture(UUID lectureId) {

        Lecture lecture = lectureRepository.findById(lectureId).orElse(null);

        if (lecture == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Lecture not found");
        }

        LectureResponse lectureResponse = LectureResponseMapper.toResponse(lecture);

        return new LectureDetailResponse("Found lecture", true, lectureResponse);
    }

    /**
     * Creating a lecture is an administrative mutation and is recorded like every other one.
     *
     * <p>It was not, for a long time: updating or deleting the same row wrote an entry and
     * creating it wrote nothing, so the audit log could show a lecture being renamed with no
     * record of where it came from. The entry is keyed {@code exists} in the same shape
     * {@code LECTURE_DELETED} uses, which is what makes it a lifecycle event rather than a field
     * diff -- {@code AuditRevertService} refuses to revert it, correctly: the inverse of a
     * creation is a deletion, which is its own action with its own guards.
     *
     * <p>Takes the principal rather than the student because {@code AuditWriter.write} records
     * the acting {@code Admin}, and a {@code Student} alone cannot say which admin acted.
     *
     * <p>There is deliberately no role check here. {@code SecurityConfig} matches
     * {@code POST /data/lectures} with {@code hasRole("ADMIN")}, and {@code /admin/**} ahead of
     * everything else, so the chain has already decided by the time this runs. The check that
     * used to stand here was unreachable, and answered {@code 200 {"success": false}} where the
     * chain answers {@code 403} -- a second, weaker answer to a question already settled.
     *
     * @param principal the principal
     * @param request the request
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional
    public BasicResponse addLecture(AuthenticatedUser principal, AddLectureRequest request) {

        // Trim once, then check and store the same value. addProfessor already stored the
        // trimmed name and this did not, so "  Algorithmen 1  " reached the column past
        // @NotBlank -- and LectureModerationService, which trims the incoming PATCH before
        // comparing, then read the very first admin edit as a name change nobody made.
        String name = trimmed(request.name());
        String code = trimmed(request.code());

        if (lectureRepository.findByName(name).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Lecture already exists: " + name);
        }


        // Null rather than @NotNull, which is how addProfessor already treats the mirror
        // field: an omitted list means "no professors yet", and the assignment is writable
        // afterwards through PATCH. Omitting it used to reach the for-each below and answer
        // 500 -- the one field of this request that @Valid was not checking.
        List<UUID> professorsIDs = request.professorIds() == null ? List.of() : request.professorIds();
        Set<Professor> professors = new HashSet<>();

        //Validate professorIDs exist
        for (UUID professorID: professorsIDs) {

            Professor professor = professorRepository.findById(professorID).orElse(null);

            if (professor == null) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Professor not found: " + professorID);
            }

            professors.add(professor);

        }


        Lecture lecture = new Lecture();

        lecture.setName(name);
        lecture.setCode(code);
        lecture.setSemesterYear(request.semesterYear());
        lecture.setSemesterSeason(request.semesterSeason());
        lecture.setActive(true);


        //Link Profs to Lecture and vice versa
        lecture.setProfessors(professors);

        for (Professor professor: professors) {
            professor.getLectures().add(lecture);
            professorRepository.save(professor);
        }

        lectureRepository.save(lecture);

        Map<String, Object> changes = AuditWriter.lifecycle(false, true);
        auditWriter.write(
                principal.admin(),
                AuditAction.LECTURE_CREATED,
                AuditTargetType.LECTURE,
                lecture.getId(),
                lecture.getName(),
                changes,
                Map.of("professors", professors.size())
        );

        return new BasicResponse("Successfully added Lectures", true);
    }


    private LecturesResponse getLectures(boolean includeInactive) {

        List<Lecture> lectures;

        if (includeInactive) {
            lectures = lectureRepository.findAllByOrderByNameAsc();
        } else {
            lectures = lectureRepository.findByActiveTrueOrderByNameAsc();
        }

        List<LectureResponse> responses = new ArrayList<>();

        for (Lecture lecture : lectures) {
            responses.add(LectureResponseMapper.toResponse(lecture));
        }

        return new LecturesResponse("Found " + responses.size() + " lectures", true, responses);
    }

    /**
     * The lecture with this id, or {@code null}.
     *
     * <p>There used to be a second copy of this taking the id as a {@code String} and calling
     * {@code UUID.fromString} for the one caller that holds it that way. Two methods running
     * the same query is one too many; the conversion belongs to the caller that has the
     * string, where the fact that a malformed id throws is at least visible.
     *
     * @param id the id
     * @return the result
     */
    public Lecture getById(UUID id) {
        return lectureRepository.findById(id).orElse(null);
    }






    /** Null-tolerant so a unit test's unstubbed field cannot turn a trim into an NPE. */
    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
