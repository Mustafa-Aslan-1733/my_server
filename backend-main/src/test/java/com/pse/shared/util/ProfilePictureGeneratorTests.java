package com.pse.shared.util;

import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilePictureGeneratorTests {

    @Test
    void activeStudentGetsProfilePictureBasedOnStudentId() {
        UUID studentId = UUID.randomUUID();

        Student student = new Student();
        student.setId(studentId);
        student.setStatus(UserStatus.ACTIVE);

        String result = ProfilePictureGenerator.generateProfilePicture(student);

        assertThat(result)
                .isEqualTo(ProfilePictureGenerator.generateProfilePicture(studentId));

        assertThat(result)
                .contains(studentId.toString());
    }


    @Test
    void deletedStudentGetsDeletedUserProfilePicture() {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setStatus(UserStatus.DELETED);

        String result = ProfilePictureGenerator.generateProfilePicture(student);

        assertThat(result)
                .contains("seed=DeletedUser");
    }


    @Test
    void deletedStudentProfilePictureDoesNotContainOriginalStudentId() {
        UUID studentId = UUID.randomUUID();

        Student student = new Student();
        student.setId(studentId);
        student.setStatus(UserStatus.DELETED);

        String result = ProfilePictureGenerator.generateProfilePicture(student);

        assertThat(result)
                .doesNotContain(studentId.toString());
    }


    @Test
    void profilePictureGeneratedFromUuidContainsUuidAsSeed() {
        UUID studentId = UUID.randomUUID();

        String result = ProfilePictureGenerator.generateProfilePicture(studentId);

        assertThat(result)
                .contains("seed=" + studentId);
    }


    @Test
    void differentStudentsGetDifferentProfilePictures() {
        UUID firstStudentId = UUID.randomUUID();
        UUID secondStudentId = UUID.randomUUID();

        String first = ProfilePictureGenerator.generateProfilePicture(firstStudentId);
        String second = ProfilePictureGenerator.generateProfilePicture(secondStudentId);

        assertThat(first).isNotEqualTo(second);
    }


    @Test
    void deletedStudentsAlwaysGetTheSameProfilePicture() {
        Student first = new Student();
        first.setId(UUID.randomUUID());
        first.setStatus(UserStatus.DELETED);

        Student second = new Student();
        second.setId(UUID.randomUUID());
        second.setStatus(UserStatus.DELETED);

        String firstPicture =
                ProfilePictureGenerator.generateProfilePicture(first);

        String secondPicture =
                ProfilePictureGenerator.generateProfilePicture(second);

        assertThat(firstPicture)
                .isEqualTo(secondPicture);
    }
}