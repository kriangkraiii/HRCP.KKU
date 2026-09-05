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
 * @param spent   whether this evaluation's course and year have already been put
 *                forward on a request that was actually submitted
 * @param spentBy the code of that request, for the explanation on screen; null
 *                when {@code spent} is false
 */
public record EvaluationChoice(EvaluationSummary summary, boolean spent, String spentBy) {

    public Long evaluationId() {
        return summary.evaluationId();
    }

    public boolean selectable() {
        return !spent;
    }
}
