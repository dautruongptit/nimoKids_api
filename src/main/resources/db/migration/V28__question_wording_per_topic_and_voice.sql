-- One question wording per topic group, with a recorded Vietnamese voice (Hải Đăng).
-- R__zz_templates.sql (runs after this file) holds the new templates and generates their questions; this file does the
-- two things a repeatable seed cannot:
--   1. switch off the questions of the retired / re-scoped templates (they are never deleted: sessions point to them),
--   2. give the templates that are NOT in R__zz_templates.sql (arithmetic) their wording and voice.
-- Idempotent.

-- 1. "What do you see?" is retired; "What is this?" now only asks about OBJECTS and "Who is this?" only about
--    PEOPLE_CHARACTERS (animals, vehicles, fruits... have their own templates: "Đây là con gì?", "Đây là quả gì?" ...).
UPDATE game_questions q
SET is_active = FALSE, updated_at = now()
FROM question_templates tpl, topics t
WHERE q.template_id = tpl.id AND q.topic_id = t.id AND q.is_active
  AND (tpl.code = 'WHAT_DO_YOU_SEE'
       OR (tpl.code = 'WHAT_IS_THIS' AND t.code <> 'OBJECTS')
       OR (tpl.code = 'WHO_IS_THIS' AND t.code <> 'PEOPLE_CHARACTERS'));

-- 2. Arithmetic: the same wording for every age and the voice file.
UPDATE question_templates
SET i18n = jsonb_set(i18n, '{vi}', '{"text": "Kết quả là bao nhiêu?", "audio": "/audio/vi/result.mp3"}'::jsonb, TRUE),
    updated_at = now()
WHERE code IN ('CALC_ADD', 'CALC_SUB', 'MATH_QUICK', 'MATH_QUICK_4_5');
