package com.pse.auth.service;

import java.util.List;
import java.util.Map;

import com.pse.auth.model.Token;
import com.pse.auth.repository.TokenRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.dto.BasicResponse;
import com.pse.user.model.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends sessions: this one, all of an account's, or all of an address's.
 *
 * <p>Separate from {@link SessionIssuer} because revoking needs two collaborators and issuing
 * needs ten. Held together they sat in one class where three quarters of the fields were
 * unreachable from half of the methods.
 */
@Service
public class SessionRevoker {

    private final TokenRepository tokenRepository;
    private final SessionAuditWriter sessionAudit;

    /**
     * Creates SessionRevoker.
     *
     * @param tokenRepository the tokenRepository
     * @param sessionAudit the sessionAudit
     */
    public SessionRevoker(TokenRepository tokenRepository, SessionAuditWriter sessionAudit) {
        this.tokenRepository = tokenRepository;
        this.sessionAudit = sessionAudit;
    }

    /**
     * Returns logout.
     *
     * @param principal the principal
     * @return the result
     */
    @Transactional
    public BasicResponse logout(AuthenticatedUser principal) {
        Token token = principal.token();
        if (!token.isRevoked()) {
            token.setRevoked(true);
            tokenRepository.save(token);
            sessionAudit.recordLogout(
                    principal,
                    token.getId(),
                    principal.student().getUsername()
                            + (principal.isAdmin() ? " admin session" : " session"),
                    Map.of()
            );
        }
        return new BasicResponse("Logged out successfully", true);
    }

    /**
     * Returns logoutAll.
     *
     * @param principal the principal
     * @return the result
     */
    @Transactional
    public BasicResponse logoutAll(AuthenticatedUser principal) {
        Student student = principal.student();
        List<Token> tokens = tokenRepository.findByStudentOrEmail(student, student.getKitEmail());
        int revoked = 0;
        for (Token token : tokens) {
            if (!token.isRevoked()) {
                token.setRevoked(true);
                revoked++;
            }
        }
        tokenRepository.saveAll(tokens);

        sessionAudit.recordLogout(
                principal,
                principal.token().getId(),
                student.getUsername() + " all sessions",
                Map.of("scope", "ALL_DEVICES", "revokedSessions", revoked)
        );
        return new BasicResponse("Logged out from all devices (" + revoked + " sessions)", true);
    }

    /**
     * Revokes every session ever minted for an address, whoever holds them. Used when an
     * account is deleted: the row goes, and so must anything still authenticating as it.
     *
     * @param email the email
     * @return the result
     */
    @Transactional
    public int invalidateAllAuthTokensForEmail(String email) {
        List<Token> tokens = tokenRepository.findByEmail(email);
        tokens.forEach(token -> token.setRevoked(true));
        tokenRepository.saveAll(tokens);
        return tokens.size();
    }
}
