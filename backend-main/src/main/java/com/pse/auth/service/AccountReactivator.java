package com.pse.auth.service;

import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.moderation.mapper.UserResponseMapper;
import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Brings a soft-deleted account back to {@code ACTIVE}, and records that it happened.
 *
 * <p>One decision lives here and nowhere else: <b>what a request for a login code does to an
 * account that has deleted itself</b>. It was written inline in
 * {@link LoginCodeService#requestLogin} and is a class of its own now for two reasons. The
 * first is transactional, below. The second is that the rule is an open product question
 * (F-48): reviving an account currently needs no proof that the caller holds the address, and
 * whichever way that is answered — require the code to be entered, move the flip to
 * {@link SessionIssuer}, or take the behaviour out — the change lands in this class instead of
 * in the middle of a method about mailing codes.
 *
 * <p><b>This class records the reactivation; it does not authorise it.</b> Nothing here checks
 * anything, because nothing checked anything before, and adding a check would be the fix rather
 * than the record. The entry it writes carries {@code callerAuthenticated: false} for that
 * reason: the fact is worth stating in every row rather than in a comment nobody reads.
 */
@Service
public class AccountReactivator {

    private final StudentRepository studentRepository;

    private final AuditWriter auditWriter;

    /**
     * Creates AccountReactivator.
     *
     * @param studentRepository the studentRepository
     * @param auditWriter the auditWriter
     */
    public AccountReactivator(StudentRepository studentRepository, AuditWriter auditWriter) {
        this.studentRepository = studentRepository;
        this.auditWriter = auditWriter;
    }

    /**
     * Sets the account back to {@code ACTIVE} and writes the entry saying so, in one
     * transaction.
     *
     * <p><b>Why this is a bean of its own rather than a private method.</b> {@code requestLogin}
     * is not transactional, and a {@code @Transactional} method called from inside its own class
     * is not proxied — the annotation would be inert and the two writes would commit separately.
     * Crossing a bean boundary is what makes the boundary real.
     *
     * <p><b>Why {@code REQUIRED} and not {@code REQUIRES_NEW}.</b>
     * {@link AuditWriter#writeRefusal} suspends its caller's transaction on purpose, because
     * every one of its callers throws immediately afterwards and a joined record would be rolled
     * back with them. The opposite is true here: there is no caller transaction to suspend, and
     * the status change and its record must share a fate. {@code REQUIRES_NEW} would be that
     * choice copied rather than made, and it would let the entry outlive a failed flip.
     *
     * <p><b>What deliberately did not change: the flip still outlives a delivery failure.</b>
     * The caller mails the code after this returns, and throws {@code 500} if the mail cannot be
     * sent. This transaction has already committed by then, so the account stays {@code ACTIVE}
     * even though the caller sees an error — exactly as it behaved before this class existed.
     * Widening the transaction to cover delivery would fix half of F-48 as a side effect of
     * refactoring it, which is not a change to make quietly.
     *
     * <p>The account instance is mutated as well as saved: the caller goes on to read
     * {@code getStatus()} from it to decide whether a code may be issued at all.
     *
     * @param account the soft-deleted account being brought back
     */
    @Transactional
    public void reactivate(Student account) {
        account.setStatus(UserStatus.ACTIVE);
        studentRepository.save(account);

        // Same label authority as the deletion entry, for the same reason: the panel recovers a
        // deleted account's identity from target_label (docs/adminweb-tasks.md section 6), and
        // the two halves of one lifecycle have to be readable side by side.
        auditWriter.writeStudentAction(
                account,
                AuditAction.USER_SELF_REACTIVATED,
                AuditTargetType.USER,
                account.getId(),
                UserResponseMapper.label(account),
                Map.of("trigger", "LOGIN_CODE_REQUEST", "callerAuthenticated", false)
        );
    }
}
