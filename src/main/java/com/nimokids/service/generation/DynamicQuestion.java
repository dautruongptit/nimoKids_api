package com.nimokids.service.generation;

import com.nimokids.entity.AnswerItem;
import com.nimokids.entity.QuestionVisual;
import java.util.List;
import java.util.Map;

/**
 * The part of a question that is decided per session (dynamic question, e.g. HOW_MANY): the correct answer item, the
 * condition that proves it, the picture to draw and an audit record. It travels through option generation and the
 * snapshot, so the picture and the answer used for grading can never disagree.
 *
 * @param correctItem   the vocabulary item that is the correct answer of THIS session question
 * @param correctMatch  the tags the correct item carries; no wrong option may carry them
 * @param visual        what the client draws (null when the picture is plain text, see {@code questionImage})
 * @param audit         what was generated (generator, count, object), stored in the snapshot
 * @param questionImage text drawn as the picture of the question, e.g. the expression "5 + 2 =" (MATH_ARITHMETIC); null
 *                      when the question has a {@code visual} or no generated text
 * @param distractors   the wrong answers chosen by the generator itself; empty = the question's distractor_rules decide
 */
public record DynamicQuestion(AnswerItem correctItem, Map<String, Object> correctMatch, QuestionVisual visual,
                              Map<String, Object> audit, String questionImage, List<AnswerItem> distractors) {

    /** A dynamic question whose wrong answers come from the question's distractor_rules and that has no text picture. */
    public DynamicQuestion(AnswerItem correctItem, Map<String, Object> correctMatch, QuestionVisual visual,
                           Map<String, Object> audit) {
        this(correctItem, correctMatch, visual, audit, null, List.of());
    }
}
