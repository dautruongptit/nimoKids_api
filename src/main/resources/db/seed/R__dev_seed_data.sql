-- DEVELOPMENT SEED DATA. Loaded only when the dev profile adds classpath:db/seed to the Flyway locations.
-- NEVER ship this to production: it contains well-known passwords (admin123).
-- Repeatable and idempotent: rows are keyed by code / deterministic id and upserted, so re-running is safe.

-- ---------------------------------------------------------------------------
-- Admin accounts. Password is a bcrypt hash (cost 10) of "admin123".
--   admin@nimokids.local  SUPER_ADMIN  can call every admin API
--   staff@nimokids.local  ADMIN        lets you see the 403 of a SUPER_ADMIN-only API
-- ---------------------------------------------------------------------------
INSERT INTO admin_users (id, email, password_hash, role) VALUES
    ('e0000000-0000-4000-8000-000000000001', 'admin@nimokids.local',
     '$2a$10$6ek66jj7pERrG5Ykaf/OqOMCj.sV3bxhFXWqBmQJzj0ylHuYyZYBG', 'SUPER_ADMIN'),
    ('e0000000-0000-4000-8000-000000000002', 'staff@nimokids.local',
     '$2a$10$6ek66jj7pERrG5Ykaf/OqOMCj.sV3bxhFXWqBmQJzj0ylHuYyZYBG', 'ADMIN')
ON CONFLICT DO NOTHING;

UPDATE admin_users SET role = 'SUPER_ADMIN'
WHERE id = 'e0000000-0000-4000-8000-000000000001' AND role <> 'SUPER_ADMIN';

-- ---------------------------------------------------------------------------
-- Game mode (one generic mode so every topic can be played with it)
-- ---------------------------------------------------------------------------
INSERT INTO game_modes (id, code, name, description) VALUES
    ('b0000000-0000-4000-8000-000000000001', 'GUESS', 'Guess', 'Look, listen and pick the right answer')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Topics: a tree (parent_id). These 14 are ROOT topics; sub-groups can be added under them later and a root then
-- draws questions from its whole subtree.
-- ---------------------------------------------------------------------------
INSERT INTO topics (code, name, slug, description, min_age, max_age, display_order) VALUES
    ('ANIMALS', 'Animals', 'animals', 'Meet friendly animals', 1, 5, 1),
    ('FRUITS', 'Fruits', 'fruits', 'Sweet and juicy', 1, 5, 2),
    ('VEHICLES', 'Vehicles', 'vehicles', 'Things that go', 1, 5, 3),
    ('COLORS', 'Colors', 'colors', 'Learn the colors', 1, 5, 4),
    ('SHAPES', 'Shapes', 'shapes', 'Circles, squares and more', 2, 5, 5),
    ('NUMBERS', 'Numbers', 'numbers', 'Count with me', 2, 5, 6),
    ('TOYS', 'Toys', 'toys', 'Time to play', 1, 5, 9),
    ('SEA_ANIMALS', 'Sea Animals', 'sea-animals', 'Life under the sea', 2, 5, 13),
    ('VEGETABLES', 'Vegetables', 'vegetables', 'Healthy veggies', 2, 5, 15),
    ('BODY_PARTS', 'Body Parts', 'body-parts', 'Head, shoulders and more', 1, 5, 16)
ON CONFLICT (code) DO NOTHING;

-- MATH is a root topic of vocabulary v2 (V16); a database where it was switched off gets it back.
UPDATE topics SET is_active = TRUE WHERE code = 'MATH' AND NOT is_active;

-- ---------------------------------------------------------------------------
-- Vocabulary v2 (V16) owns the topic tree and the words. This seed no longer carries its own old sample vocabulary and
-- hand-written questions (removed together with V31). On a fresh database the old rows that the versioned migrations
-- created are switched off here: v2 answer items all carry metadata.age_group; the old ones do not.
-- ---------------------------------------------------------------------------
-- The hand-written (CURATED) questions are not used any more (short template questions only); switched off here as well
-- as in R__zz_templates.sql (which runs only when it changes).
UPDATE game_questions
SET is_active = FALSE, updated_at = now()
WHERE is_active
  AND (template_id IS NULL
       OR template_id IN (SELECT id FROM question_templates WHERE kind <> 'TEMPLATE'));

UPDATE answer_items
SET is_active = FALSE, updated_at = now()
WHERE is_active AND NOT (metadata ? 'age_group');

-- ---------------------------------------------------------------------------
-- Stickers. FIRST_GAME and PERFECT_SCORE are awarded by rules in the service.
-- ANIMAL_LOVER has no award rule defined yet in the business documents.
-- ---------------------------------------------------------------------------
INSERT INTO stickers (id, code, name, description, rarity) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'FIRST_GAME', 'First Game', 'You finished your first game!', 'COMMON'),
    ('d0000000-0000-4000-8000-000000000002', 'PERFECT_SCORE', 'Perfect Score', 'All answers correct!', 'RARE'),
    ('d0000000-0000-4000-8000-000000000003', 'ANIMAL_LOVER', 'Animal Lover', 'You love animals!', 'COMMON')
ON CONFLICT DO NOTHING;
