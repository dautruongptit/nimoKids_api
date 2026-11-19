-- Removes the topics OBJECTS ("Các đồ vật") and TOYS ("Đồ chơi") for good, with their questions and their words.
-- Idempotent: on a database without these topics every statement deletes nothing.

-- Play sessions of these topics, or that contain one of their questions: question snapshots are frozen inside a session and
-- session_questions.question_id / game_sessions.topic_id are RESTRICT, so the whole session goes (its session_questions
-- cascade). activity_logs / api_logs / player_stickers keep their rows (session_id SET NULL).
CREATE TEMP TABLE doomed_topics ON COMMIT DROP AS
SELECT id FROM topics WHERE code IN ('OBJECTS', 'TOYS');

CREATE TEMP TABLE doomed_sessions ON COMMIT DROP AS
SELECT s.id FROM game_sessions s WHERE s.topic_id IN (SELECT id FROM doomed_topics)
UNION
SELECT sq.session_id FROM session_questions sq JOIN game_questions q ON q.id = sq.question_id
WHERE q.topic_id IN (SELECT id FROM doomed_topics);

DELETE FROM game_sessions WHERE id IN (SELECT id FROM doomed_sessions);

-- Questions (question_stickers cascade), then the words nothing points to any more, then the topics (children first).
DELETE FROM game_questions WHERE topic_id IN (SELECT id FROM doomed_topics);

DELETE FROM answer_items a
WHERE a.metadata ->> 'category' IN ('objects', 'toys')
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.correct_answer_item_id = a.id);

DELETE FROM topics WHERE parent_id IN (SELECT id FROM doomed_topics);
DELETE FROM topics WHERE id IN (SELECT id FROM doomed_topics);
