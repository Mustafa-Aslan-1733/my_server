package com.pse.user.service;

import com.pse.shared.dto.BasicResponse;
import com.pse.user.dto.UserRatingResponse;
import com.pse.auth.service.SessionRevoker;
import com.pse.user.dto.StudentProfileResponse;
import com.pse.user.model.Student;
import org.springframework.stereotype.Service;

/**
 * Provides AccountService.
 */
@Service
public class AccountService {

    private final SessionRevoker sessionRevoker;

    private final StudentService studentService;


    /**
     * Creates AccountService.
     *
     * @param sessionRevoker the sessionRevoker
     * @param studentService the studentService
     */
    public AccountService(SessionRevoker sessionRevoker, StudentService studentService) {
        this.sessionRevoker = sessionRevoker;
        this.studentService = studentService;
    }


    /**
     * Returns getUserRatings.
     *
     * @param student the student
     * @return the result
     */
    public UserRatingResponse getUserRatings(Student student) {
        return studentService.getUserRatings(student.getId());
    }

    /**
     * Returns getUserInformation.
     *
     * @param student the student
     * @return the result
     */
    public StudentProfileResponse getUserInformation(Student student) {
        return studentService.getUserInformation(student.getId());
    }

    /**
     * Returns deleteAccount.
     *
     * @param student the student
     * @return the result
     */
    public BasicResponse deleteAccount(Student student) {

        // Since F-5 the failure arrives as a thrown ApiException, so an unsuccessful
        // deletion never reaches the revocation below at all. The condition is kept as the
        // statement of the rule rather than as the only thing enforcing it: without it, an
        // account that was not deleted would still be signed out of every session, and if
        // the failure signalling ever changes back the rule survives the change.
        BasicResponse response = studentService.deleteAccount(student);

        if (response.success()) {
            // Without this the account is DELETED but every issued token stays
            // valid until it expires, which for a student is a full year.
            sessionRevoker.invalidateAllAuthTokensForEmail(student.getKitEmail());
        }

        return response;
    }

    /**
     * Returns requestLogout.
     *
     * @param student the student
     * @return the result
     */
    public BasicResponse requestLogout(Student student) {


        //Iterate through every entry in AuthToken and delete every match where: AuthToken.email == email
        int sessions = sessionRevoker.invalidateAllAuthTokensForEmail(student.getKitEmail());

        return new BasicResponse("Logged out successfully (" + sessions + " sessions)", true);
    }



}
