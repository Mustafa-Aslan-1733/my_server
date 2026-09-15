package com.pse.support;

/**
 * Protocol-level answers this API gives, held in one place so a decision to change one is a
 * single edit rather than a search across the suite.
 *
 * <p>They live here rather than in the test that sweeps them because other suites assert
 * them incidentally -- {@link com.pse.AdminApiPathSplitTests} depends on
 * {@link #WRONG_VERB_STATUS} to describe an unrelated routing fact.
 *
 * <p>Both constants now hold the right answer. They stay constants anyway: the sweep that
 * reads {@link #WRONG_VERB_STATUS} covers every mapped path and the one that reads
 * {@link #UNSUPPORTED_MEDIA_TYPE_STATUS} covers every body-reading route, so a decision to
 * change either is still a single edit rather than a search across the suite.
 */
public final class ApiContract {

    /**
     * A path that exists, called with a verb it does not map -- F-16 in
     * docs/test-findings.md, now fixed. {@code GlobalExceptionHandler.handleWrongMethod}
     * answers 405 "Method not allowed" and names the mapped verbs in an {@code Allow}
     * header. It answered 404 with the same body as an unknown path until then, which is
     * why the two mechanisms were indistinguishable from the response.
     */
    public static final int WRONG_VERB_STATUS = 405;

    /**
     * A body in a media type the handler cannot read -- F-17, now fixed.
     * {@code GlobalExceptionHandler.handleUnsupportedMediaType} answers 415 with the
     * project's {@code BasicResponse} shape. Kept as a constant rather than inlined
     * because the sweep that reads it covers 37 routes and the value is the contract.
     */
    public static final int UNSUPPORTED_MEDIA_TYPE_STATUS = 415;

    private ApiContract() {
    }
}
