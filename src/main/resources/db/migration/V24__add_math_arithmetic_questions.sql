-- MATH_ARITHMETIC (CALCULATE topic): a very easy sum or difference drawn for every session, answered inside the
-- countdown ("Toán cộng trừ siêu tốc").
--   picture        : the expression "5 + 2 =", generated per session and frozen in the session snapshot
--                    (DynamicQuestion.questionImage), so it is shown as large text instead of an <img>
--   correct answer : the number item whose value is the result      (answer_items, category 'arithmetic_number')
--   wrong answers  : 3 other numbers of 0..maxResult, chosen by the generator from the same category
--   time limit     : game_questions.time_limit_seconds (the client counts down, the server enforces it)
-- The rule is metadata.generator of the question row; see ArithmeticQuestionGenerator for every key.
-- The templates deliberately have no "generator" / "topics" key: R__zz_templates.sql builds COUNT_OBJECTS rows for those.

-- 1. The number vocabulary 0..20 -----------------------------------------------------------------------------------
--    No audio is set: the clients read the text aloud. When recorded files exist, add them per language, e.g.
--    i18n = {"vi": {"text": "7", "audio": "/audio/vi/7.mp3"}, "en": {"text": "7", "audio": "/audio/en/7.mp3"}}.
INSERT INTO answer_items (code, name, metadata, i18n, is_active)
SELECT 'ARITH_NUMBER_' || n, n::text,
       jsonb_build_object('category', 'arithmetic_number', 'value', n, 'emoji', n::text, 'features', '[]'::jsonb,
                          'item', 'ARITH_NUMBER_' || n),
       jsonb_build_object('en', jsonb_build_object('text', n::text), 'vi', jsonb_build_object('text', n::text)),
       TRUE
FROM generate_series(0, 20) AS n
ON CONFLICT (code) DO UPDATE
    SET name = EXCLUDED.name, metadata = EXCLUDED.metadata, i18n = EXCLUDED.i18n, is_active = TRUE, updated_at = now();

-- 2. One shared wording per age group ------------------------------------------------------------------------------
INSERT INTO question_templates (code, kind, question_type, age_group, difficulty, i18n, generation, is_active) VALUES
    ('MATH_QUICK', 'TEMPLATE', 'SIMPLE_ADDITION', 'AGE_1_3', 1,
     '{"en": {"text": "What is the result?"}, "vi": {"text": "Kết quả là:"}}',
     '{"status": "ACTIVE", "source": "MATH_ARITHMETIC"}', TRUE),
    ('MATH_QUICK_4_5', 'TEMPLATE', 'SIMPLE_ADDITION', 'AGE_4_5', 2,
     '{"en": {"text": "What is the result?"}, "vi": {"text": "Kết quả là:"}}',
     '{"status": "ACTIVE", "source": "MATH_ARITHMETIC"}', TRUE)
ON CONFLICT (code) DO UPDATE
    SET question_type = EXCLUDED.question_type, age_group = EXCLUDED.age_group, difficulty = EXCLUDED.difficulty,
        i18n = EXCLUDED.i18n, generation = EXCLUDED.generation, is_active = EXCLUDED.is_active, updated_at = now()
    WHERE question_templates.kind = 'TEMPLATE';

-- 3. Five rows per age group: a session meets the same rule several times, each with a new expression ---------------
--    1-3 : sums and differences of 1..5, result at most 10, 8 seconds.
--    4-5 : operands up to 6, result at most 12, plus the round pairs 5 + 5, 10 - 5, 10 + 2 (easyPairs), 10 seconds.
INSERT INTO game_questions
    (id, topic_id, game_mode_id, question_text, correct_answer_item_id, difficulty, min_age, max_age, display_order,
     time_limit_seconds, metadata, is_active, question_key, age_group, question_type, template_id)
SELECT md5('nimokids:math-quick:' || tpl.code || ':' || slot.n)::uuid,
       t.id, m.id, tpl.i18n #>> '{en,text}', NULL, tpl.difficulty,
       CASE tpl.age_group WHEN 'AGE_1_3' THEN 1 ELSE 4 END, CASE tpl.age_group WHEN 'AGE_1_3' THEN 3 ELSE 5 END,
       400 + slot.n,
       CASE tpl.age_group WHEN 'AGE_1_3' THEN 8 ELSE 10 END,
       jsonb_build_object(
           'generator', CASE tpl.age_group
               WHEN 'AGE_1_3' THEN jsonb_build_object(
                   'type', 'MATH_ARITHMETIC', 'operators', jsonb_build_array('+', '-'), 'minOperand', 1, 'maxOperand', 5,
                   'maxResult', 10, 'allowNegative', false, 'optionCount', 4, 'easyPairs', false,
                   'numberCategory', 'arithmetic_number')
               ELSE jsonb_build_object(
                   'type', 'MATH_ARITHMETIC', 'operators', jsonb_build_array('+', '-'), 'minOperand', 1, 'maxOperand', 6,
                   'maxResult', 12, 'allowNegative', false, 'optionCount', 4, 'easyPairs', true,
                   'numberCategory', 'arithmetic_number')
           END,
           'source', 'TEMPLATE'),
       TRUE, left('TPL_' || tpl.code || '_' || slot.n, 50), tpl.age_group, tpl.question_type, tpl.id
FROM question_templates tpl
CROSS JOIN generate_series(1, 5) AS slot(n)
JOIN topics t ON t.code = 'CALCULATE' AND t.is_active
JOIN game_modes m ON m.code = 'GUESS'
WHERE tpl.code IN ('MATH_QUICK', 'MATH_QUICK_4_5') AND tpl.kind = 'TEMPLATE' AND tpl.is_active
ON CONFLICT (id) DO UPDATE
    SET question_text = EXCLUDED.question_text, metadata = EXCLUDED.metadata, template_id = EXCLUDED.template_id,
        question_type = EXCLUDED.question_type, age_group = EXCLUDED.age_group, difficulty = EXCLUDED.difficulty,
        min_age = EXCLUDED.min_age, max_age = EXCLUDED.max_age, time_limit_seconds = EXCLUDED.time_limit_seconds,
        is_active = TRUE, updated_at = now();
