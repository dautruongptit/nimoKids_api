-- Removes everything that is switched off and no longer used: inactive questions (with the play sessions that contain them),
-- inactive answer items nothing points to, and the retired WHAT_DO_YOU_SEE template. The still-planned templates
-- (WHICH_BIGGER, WHO_SAYS ... status PLANNED) are the roadmap and stay. Inactive topics were removed by V27.
-- Idempotent: on a database without such rows every statement deletes nothing.

-- A question that is on but whose correct answer is off cannot be played correctly: switch it off first so it goes too.
UPDATE game_questions q
SET is_active = FALSE, updated_at = now()
FROM answer_items a
WHERE q.correct_answer_item_id = a.id AND q.is_active AND NOT a.is_active;

-- Sessions that contain an inactive question: the question snapshots are frozen inside them, so the whole session goes
-- (session_questions.question_id is ON DELETE RESTRICT). activity_logs.session_id / question_id are ON DELETE SET NULL:
-- the log lines stay.
CREATE TEMP TABLE doomed_sessions ON COMMIT DROP AS
SELECT DISTINCT sq.session_id AS id
FROM session_questions sq JOIN game_questions q ON q.id = sq.question_id
WHERE NOT q.is_active;

DELETE FROM session_questions WHERE session_id IN (SELECT id FROM doomed_sessions);
DELETE FROM game_sessions WHERE id IN (SELECT id FROM doomed_sessions);

DELETE FROM game_questions WHERE NOT is_active;

-- Inactive answer items that no question points to (game_questions.correct_answer_item_id is RESTRICT).
DELETE FROM answer_items a
WHERE NOT a.is_active
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.correct_answer_item_id = a.id);

DELETE FROM question_templates t
WHERE t.code = 'WHAT_DO_YOU_SEE'
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.template_id = t.id);
