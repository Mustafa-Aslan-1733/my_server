package com.pse.shared.util;

import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;

import java.util.UUID;

/**
 * Provides ProfilePictureGenerator.
 */
public final class ProfilePictureGenerator {

    private static final String DICEBEAR_BASE_URL =
            "https://api.dicebear.com/10.x/thumbs/svg"
                    + "?eyesProbability=100"
                    + "&eyesVariant=variant02,variant03,variant04,variant05,variant06,variant07,variant08"
                    + "&seed=";

    private static final String DELETED_USER_PROFILE_PICTURE =
            "https://api.dicebear.com/10.x/thumbs/svg"
                    + "?backgroundColor=343437,5e5e62,8c8c90,b6b6b9"
                    + "&shapeColor=c4c4c8,9a9a9e,6e6e72"
                    + "&animationProbability=100"
                    + "&eyesVariant=variant08"
                    + "&mouthVariant=variant03"
                    + "&seed=DeletedUser";

    private ProfilePictureGenerator() {
        // Utility class
    }


    /**
     * Returns the profile picture for a student.
     *
     * @param student the student
     * @return the profile picture URL
     */
    public static String generateProfilePicture(Student student) {
        if (student.getStatus() == UserStatus.DELETED) {
            return DELETED_USER_PROFILE_PICTURE;
        }

        return generateProfilePicture(student.getId());
    }



    /**
     * Returns generateProfilePicture.
     *
     * @param studentId the studentId
     * @return the result
     */
    public static String generateProfilePicture(UUID studentId) {
        return DICEBEAR_BASE_URL + studentId;
    }
}
