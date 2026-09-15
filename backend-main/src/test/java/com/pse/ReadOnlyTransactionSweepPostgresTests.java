package com.pse;

import com.pse.auth.repository.TokenRepository;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.moderation.repository.AdminRepository;
import com.pse.professor.model.Professor;
import com.pse.professor.repository.ProfessorRepository;
import com.pse.shared.enums.SemesterSeason;
import com.pse.support.AdminSessions;
import com.pse.support.MappedRoutes;
import com.pse.support.PostgresIntegrationTest;
import com.pse.user.repository.StudentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Every readable route, driven against a database that enforces {@code readOnly}.
 *
 * <p>F-9 was a write inside a {@code @Transactional(readOnly = true)} method, and
 * {@code docs/test-findings.md} records why a unit test could not settle it: dirty checking is
 * Hibernate's business, not a mock's, so there is no such thing as the defect happening against
 * a mocked repository. It was fixed by removing the write, which left the general question
 * open — there are 27 such methods in {@code src/main/java} and only one of them had ever been
 * looked at.
 *
 * <p>PostgreSQL is what makes the question answerable. It marks the transaction read-only on
 * the server, so a write that reaches it fails with {@code cannot execute UPDATE in a read-only
 * transaction} — surfacing as a 500 here. H2 does not, which is why this sweep cannot live in
 * the main test job and says nothing when it is skipped there.
 *
 * <p>The route list comes from {@link MappedRoutes}, so a GET added tomorrow is swept without
 * anybody remembering to add it. A route whose path variables this fixture cannot fill, or
 * which answers something other than 2xx, is <em>named</em> rather than quietly dropped: a
 * sweep is only worth what its coverage is written down as, and "no route returned 500" is
 * satisfied trivially by a sweep where every route returned 400.
 */
@PostgresIntegrationTest
class ReadOnlyTransactionSweepPostgresTests {

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}]+)}");

    /**
     * Routes that answer something other than 2xx to this fixture, with the reason. They are
     * still swept — a 500 from any of them still fails the test above — but they do not prove
     * a read-only transaction was entered, so they are listed rather than counted.
     */
    private static final Map<String, String> EXPECTED_NON_2XX = new LinkedHashMap<>();

    static {
        // Two required @RequestParams, and this sweep sends none: F-14 made a missing required
        // parameter answer 400 instead of 500, so this is the fixed behaviour, not a gap. The
        // handler is not @Transactional at all -- it delegates to ProfessorService.getProfessorId
        // -- so nothing is lost by not entering it.
        EXPECTED_NON_2XX.put("GET /data/professor/id", "400: needs firstName and lastName");
    }

    @Autowired MockMvc mockMvc;
    @Autowired RequestMappingHandlerMapping handlerMapping;
    @Autowired StudentRepository studentRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired TokenRepository tokenRepository;
    @Autowired LectureRepository lectureRepository;
    @Autowired ProfessorRepository professorRepository;

    private String adminToken;
    private Map<String, String> pathVariables;

    @BeforeEach
    void buildFixture() {
        lectureRepository.deleteAll();
        professorRepository.deleteAll();
        adminToken = AdminSessions.createAdmin(
                "admin@student.kit.edu", studentRepository, adminRepository, tokenRepository);

        Lecture lecture = new Lecture();
        lecture.setName("Algorithmen 1");
        lecture.setCode("IN0001");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setActive(true);
        UUID lectureId = lectureRepository.saveAndFlush(lecture).getId();

        Professor professor = new Professor();
        professor.setFirstName("Peter");
        professor.setLastName("Sanders");
        UUID professorId = professorRepository.saveAndFlush(professor).getId();

        UUID studentId = studentRepository.findAll().getFirst().getId();

        // Keyed by the placeholder's own name, so a route added tomorrow resolves without an
        // entry here as long as it spells its id the way the existing ones do. One that does
        // not is reported by the test below rather than silently skipped.
        pathVariables = Map.of(
                "lectureId", lectureId.toString(),
                "lecture_id", lectureId.toString(),
                "professor_id", professorId.toString(),
                "id", studentId.toString());
    }

    @Test
    void noReadableRouteWritesInsideItsReadOnlyTransaction() throws Exception {
        Map<String, Integer> statuses = sweep();

        Set<String> serverErrors = new TreeSet<>();
        statuses.forEach((route, status) -> {
            if (status >= 500) {
                serverErrors.add(route + " -> " + status);
            }
        });

        assertThat(serverErrors)
                .as("PostgreSQL refuses a write inside a read-only transaction, and that "
                        + "refusal arrives here as a 500. F-9 was one of these.")
                .isEmpty();
    }

    /**
     * What the sweep above actually reached. Without this, every route could be answering 400
     * and the assertion would still be green while entering no transaction at all.
     */
    @Test
    void theSweepReachesEveryReadableRouteItClaimsTo() throws Exception {
        Map<String, Integer> statuses = sweep();

        Map<String, Integer> nonSuccess = new TreeMap<>();
        statuses.forEach((route, status) -> {
            if (status < 200 || status >= 300) {
                nonSuccess.put(route, status);
            }
        });

        assertThat(nonSuccess.keySet())
                .as("a route that stopped answering 2xx is no longer proving anything about "
                        + "read-only transactions -- record why, or fix the fixture")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_NON_2XX.keySet());
        // A floor, and it is meant to be edited: the legacy /admin-twin paths are scheduled for
        // deletion (docs/TODO.md, "Waiting on other people"), and when they go this number drops
        // on purpose. What it stops is the number dropping by accident, which is how a sweep
        // ends up green while covering half of what it did.
        assertThat(statuses.size() - nonSuccess.size())
                .as("routes that actually entered a read transaction and answered from it")
                .isGreaterThanOrEqualTo(47);
    }

    @Test
    void everyGetRoutesPathVariablesCanBeFilledFromTheFixture() {
        Set<String> unresolvable = new TreeSet<>();

        for (MappedRoutes.Route route : readableRoutes()) {
            Matcher matcher = PATH_VARIABLE.matcher(route.pattern());
            while (matcher.find()) {
                if (!pathVariables.containsKey(matcher.group(1))) {
                    unresolvable.add(route.verbAndPattern() + " needs {" + matcher.group(1) + "}");
                }
            }
        }

        assertThat(unresolvable)
                .as("an unfillable placeholder means this sweep is quietly covering less than "
                        + "the route list says -- add it to the fixture")
                .isEmpty();
    }

    private Map<String, Integer> sweep() throws Exception {
        Map<String, Integer> statuses = new TreeMap<>();

        for (MappedRoutes.Route route : readableRoutes()) {
            int status = mockMvc.perform(get(fill(route.pattern()))
                            .header("Authorization", "Bearer " + adminToken))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            statuses.put(route.verbAndPattern(), status);
        }

        return statuses;
    }

    /** Every GET this project publishes. A verb-less mapping answers GET too, so it counts. */
    private Iterable<MappedRoutes.Route> readableRoutes() {
        return MappedRoutes.application(handlerMapping).stream()
                .filter(route -> route.verb() == null || route.verb().equals("GET"))
                .toList();
    }

    private String fill(String pattern) {
        Matcher matcher = PATH_VARIABLE.matcher(pattern);
        StringBuilder path = new StringBuilder();
        while (matcher.find()) {
            String value = pathVariables.getOrDefault(matcher.group(1), UUID.randomUUID().toString());
            matcher.appendReplacement(path, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(path);
        return path.toString();
    }
}
