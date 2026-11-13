-- The MATH_QUICK rows added by V26 had no metadata.distractor_rules. The candidate query of the session engine keeps only
-- questions whose distractor_rules is an array, so these rows were never offered (CALCULATE at 3 years had 0 candidates).
-- The marker is not used to build the options (the generator picks the wrong numbers itself); it only has to be an array.
-- Idempotent: only rows without the array are touched.
UPDATE game_questions
SET metadata = metadata || '{"distractor_rules": [{"match": {"category": "arithmetic_number"}, "count": 3}]}'::jsonb,
    updated_at = now()
WHERE question_key LIKE 'TPL\_MATH\_QUICK%'
  AND jsonb_typeof(metadata -> 'distractor_rules') IS DISTINCT FROM 'array';
