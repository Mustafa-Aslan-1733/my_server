package com.pse.support;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditActorType;
import com.pse.audit.model.AuditLog;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.repository.AuditLogRepository;
import com.pse.lecture.model.Lecture;
import com.pse.lecture.repository.LectureRepository;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard on {@link DatabaseReset}'s table list.
 *
 * <p>That list is written by hand, and a hand-written list of anything is what goes stale in
 * silence: a new entity would simply never be emptied, and the symptom would not be a failure
 * here but a puzzling one somewhere else, in whichever test happened to run after the one that
 * left rows behind. So the list is checked against the mapping Hibernate actually built.
 *
 * <p>Same discipline as the route sweeps, for the same reason -- a list that describes the
 * system has to be derived from the system, or asserted against it.
 */
@ApiIntegrationTest
class DatabaseResetTests {

    @Autowired EntityManager entityManager;
    @Autowired DatabaseReset databaseReset;
    @Autowired StudentRepository studentRepository;
    @Autowired LectureRepository lectureRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @Test
    void everyMappedEntityIsEmptiedOrNamedAsACascade() {
        Set<Class<?>> mapped = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .collect(Collectors.toSet());

        Set<Class<?>> accountedFor = new HashSet<>(databaseReset.deletedEntities());
        accountedFor.addAll(DatabaseReset.DELETED_BY_CASCADE);

        Set<Class<?>> unaccounted = new HashSet<>(mapped);
        unaccounted.removeAll(accountedFor);

        assertThat(unaccounted)
                .as("a mapped entity nothing empties will leak rows into the next test -- add it "
                        + "to DatabaseReset in foreign-key order, or to DELETED_BY_CASCADE if a "
                        + "parent already removes it")
                .isEmpty();
    }

    @Test
    void theListNamesNothingThatIsNoLongerMapped() {
        Set<Class<?>> mapped = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .collect(Collectors.toSet());

        Set<Class<?>> stale = new HashSet<>(databaseReset.deletedEntities());
        stale.addAll(DatabaseReset.DELETED_BY_CASCADE);
        stale.removeAll(mapped);

        assertThat(stale)
                .as("an entry for an entity that no longer exists is a list nobody has read")
                .isEmpty();
    }

    /**
     * That the list is complete says nothing about whether {@code all()} runs. Three tables from
     * opposite ends of the deletion order, seeded and then emptied.
     */
    @Test
    void allEmptiesTheTablesItNames() {
        Student student = new Student();
        student.setKitEmail("reset@student.kit.edu");
        student.setUsername("reset");
        student.setStatus(UserStatus.ACTIVE);
        student.setEmailVerifiedAt(LocalDateTime.now());
        studentRepository.saveAndFlush(student);

        Lecture lecture = new Lecture();
        lecture.setName("Algorithmen 1");
        lecture.setCode("IN0001");
        lecture.setSemesterYear(2026);
        lecture.setSemesterSeason(SemesterSeason.SS);
        lecture.setActive(true);
        lectureRepository.saveAndFlush(lecture);

        AuditLog log = new AuditLog();
        log.setActorType(AuditActorType.USER);
        log.setActorId(UUID.randomUUID());
        log.setActorName("ada");
        log.setActorEmail("ada@kit.edu");
        log.setActorRole("STUDENT");
        log.setAction(AuditAction.COMMENT_CREATED);
        log.setTargetType(AuditTargetType.COMMENT);
        log.setTargetId(UUID.randomUUID());
        log.setTargetLabel("IN0001");
        log.setChanges(Map.of());
        log.setMetadata(Map.of());
        auditLogRepository.saveAndFlush(log);

        databaseReset.all();

        assertThat(studentRepository.count()).isZero();
        assertThat(lectureRepository.count()).isZero();
        assertThat(auditLogRepository.count()).isZero();
    }
}
