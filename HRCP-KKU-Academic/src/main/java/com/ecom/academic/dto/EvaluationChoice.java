package com.ecom.academic.dto;

/**
 * One row on the "choose which teaching evaluation to apply with" screen.
 *
 * <p>A spent evaluation is carried here rather than dropped. The rule is easy to
 * hit by accident — an applicant who was evaluated on the same course twice, or
 * who has forgotten which one an earlier request used — and a list that simply
 * comes up one shorter than expected gives them nothing to reason about. Shown
 * and struck through, naming the request that took it, it answers the question
 * instead of raising it.
 *
 * <p>The same goes for an evaluation that is still under way, failed or has
 * lapsed: an applicant whose only evaluation is still being assessed used to see
 * an empty screen and no reason for it.
 *
 * @param spent             whether this evaluation's course and year have already
 *                          been put forward on a request that was actually submitted
 * @param spentBy           the code of that request, for the explanation on screen;
 *                          null when {@code spent} is false
 * @param unavailableReason why this evaluation cannot back a request at all, for
 *                          the explanation on screen; null when it can
 */
public record EvaluationChoice(EvaluationSummary summary, boolean spent, String spentBy,
        String unavailableReason) {

    public EvaluationChoice(EvaluationSummary summary, boolean spent, String spentBy) {
        this(summary, spent, spentBy, null);
    }

    public Long evaluationId() {
        return summary.evaluationId();
    }

    /** A passed, unexpired result — whether or not a request has already taken it. */
    public boolean usable() {
        return unavailableReason == null;
    }

    public boolean selectable() {
        return usable() && !spent;
    }
}
