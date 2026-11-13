-- Removes the ten old topics that V16 switched off (Alphabet, Food, Clothes, Home, Nature, Farm Animals, Weather, Family,
-- Music & Instruments, Daily Activities) together with their questions, sessions, template and old vocabulary.
-- Idempotent: on a database where they are already gone every statement deletes nothing.

-- Questions of these topics were frozen into session snapshots; the sessions and their question links go first
-- (game_sessions.topic_id and session_questions.question_id are ON DELETE RESTRICT).
DELETE FROM session_questions
WHERE question_id IN (SELECT q.id FROM game_questions q JOIN topics t ON t.id = q.topic_id
                      WHERE t.code IN ('ALPHABET', 'FOOD', 'CLOTHES', 'HOME', 'NATURE', 'FARM_ANIMALS', 'WEATHER',
                                       'FAMILY', 'MUSIC_INSTRUMENTS', 'DAILY_ACTIVITIES') AND NOT t.is_active);

-- activity_logs.session_id / question_id / topic_id are ON DELETE SET NULL: the log lines stay.
DELETE FROM game_sessions
WHERE topic_id IN (SELECT id FROM topics
                   WHERE code IN ('ALPHABET', 'FOOD', 'CLOTHES', 'HOME', 'NATURE', 'FARM_ANIMALS', 'WEATHER',
                                  'FAMILY', 'MUSIC_INSTRUMENTS', 'DAILY_ACTIVITIES') AND NOT is_active);

DELETE FROM game_questions
WHERE topic_id IN (SELECT id FROM topics
                   WHERE code IN ('ALPHABET', 'FOOD', 'CLOTHES', 'HOME', 'NATURE', 'FARM_ANIMALS', 'WEATHER',
                                  'FAMILY', 'MUSIC_INSTRUMENTS', 'DAILY_ACTIVITIES') AND NOT is_active);

DELETE FROM topics
WHERE code IN ('ALPHABET', 'FOOD', 'CLOTHES', 'HOME', 'NATURE', 'FARM_ANIMALS', 'WEATHER', 'FAMILY',
               'MUSIC_INSTRUMENTS', 'DAILY_ACTIVITIES')
  AND NOT is_active
  AND NOT EXISTS (SELECT 1 FROM topics child WHERE child.parent_id = topics.id)
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.topic_id = topics.id);

-- The "What letter is this?" template only served ALPHABET.
DELETE FROM question_templates
WHERE code = 'WHAT_LETTER'
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.template_id = question_templates.id);

-- Old (pre-V16) vocabulary of those groups: switched off, no age_group tag, not the answer of any question.
DELETE FROM answer_items a
WHERE NOT a.is_active
  AND NOT (a.metadata ? 'age_group')
  AND a.metadata ->> 'category' IN ('alphabet', 'clothes', 'daily_activities', 'family', 'farm_animals', 'food', 'home',
                                    'music_instruments', 'nature', 'weather')
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.correct_answer_item_id = a.id);
