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

-- ---------------------------------------------------------------------------
-- Answer items: the vocabulary of every group. Tags are EXPLICIT positive facts:
--   category : the group (wrong answers are drawn only from the same group)
--   features : EVERYTHING that is true about the item for the questions of its group. An item that is also a fair
--              answer to a question carries that question's feature, so it is never shown as a wrong answer.
--   emoji    : picture fallback until image assets exist
-- ---------------------------------------------------------------------------
INSERT INTO answer_items (code, name, metadata) VALUES
    ('ANIMALS_CAT', 'Cat', '{"category":"animals","emoji":"🐱","features":["says_meow"]}'),
    ('ANIMALS_DOG', 'Dog', '{"category":"animals","emoji":"🐶","features":["says_woof"]}'),
    ('ANIMALS_ELEPHANT', 'Elephant', '{"category":"animals","emoji":"🐘","features":["long_trunk"]}'),
    ('ANIMALS_GIRAFFE', 'Giraffe', '{"category":"animals","emoji":"🦒","features":["long_neck"]}'),
    ('ANIMALS_ZEBRA', 'Zebra', '{"category":"animals","emoji":"🦓","features":["black_white_stripes"]}'),
    ('ANIMALS_LION', 'Lion', '{"category":"animals","emoji":"🦁","features":["has_mane"]}'),
    ('ANIMALS_RABBIT', 'Rabbit', '{"category":"animals","emoji":"🐰","features":["can_hop"]}'),
    ('ANIMALS_COW', 'Cow', '{"category":"animals","emoji":"🐮","features":["gives_milk"]}'),
    ('ANIMALS_TURTLE', 'Turtle', '{"category":"animals","emoji":"🐢","features":["has_shell"]}'),
    ('ANIMALS_MONKEY', 'Monkey', '{"category":"animals","emoji":"🐵","features":["swings_from_trees"]}'),
    ('ANIMALS_PIG', 'Pig', '{"category":"animals","emoji":"🐷","features":[]}'),
    ('ANIMALS_BEAR', 'Bear', '{"category":"animals","emoji":"🐻","features":[]}'),
    ('ANIMALS_MOUSE', 'Mouse', '{"category":"animals","emoji":"🐭","features":[]}'),
    ('ANIMALS_FISH', 'Fish', '{"category":"animals","emoji":"🐟","features":[]}'),
    ('FRUITS_BANANA', 'Banana', '{"category":"fruits","emoji":"🍌","features":["yellow_long","grows_in_bunch"]}'),
    ('FRUITS_APPLE', 'Apple', '{"category":"fruits","emoji":"🍎","features":["red_round"]}'),
    ('FRUITS_ORANGE', 'Orange', '{"category":"fruits","emoji":"🍊","features":["orange_round"]}'),
    ('FRUITS_GRAPE', 'Grape', '{"category":"fruits","emoji":"🍇","features":["grows_in_bunch"]}'),
    ('FRUITS_WATERMELON', 'Watermelon', '{"category":"fruits","emoji":"🍉","features":["green_outside_red_inside"]}'),
    ('FRUITS_PINEAPPLE', 'Pineapple', '{"category":"fruits","emoji":"🍍","features":["has_crown"]}'),
    ('FRUITS_STRAWBERRY', 'Strawberry', '{"category":"fruits","emoji":"🍓","features":["red_round","seeds_outside"]}'),
    ('FRUITS_PEAR', 'Pear', '{"category":"fruits","emoji":"🍐","features":["pear_shaped"]}'),
    ('FRUITS_KIWI', 'Kiwi', '{"category":"fruits","emoji":"🥝","features":["hairy_green_inside"]}'),
    ('FRUITS_PLUM', 'Plum', '{"category":"fruits","emoji":"🟣","features":["big_pit_purple_or_yellow"]}'),
    ('FRUITS_LEMON', 'Lemon', '{"category":"fruits","emoji":"🍋","features":[]}'),
    ('FRUITS_CHERRY', 'Cherry', '{"category":"fruits","emoji":"🍒","features":["red_round","grows_in_bunch","big_pit_purple_or_yellow"]}'),
    ('VEHICLES_AIRPLANE', 'Airplane', '{"category":"vehicles","emoji":"✈️","features":["flies_in_sky"]}'),
    ('VEHICLES_BOAT', 'Boat', '{"category":"vehicles","emoji":"⛵","features":["travels_on_water"]}'),
    ('VEHICLES_TRAIN', 'Train', '{"category":"vehicles","emoji":"🚆","features":["goes_choo_choo"]}'),
    ('VEHICLES_BICYCLE', 'Bicycle', '{"category":"vehicles","emoji":"🚲","features":["two_wheels_pedals"]}'),
    ('VEHICLES_SCHOOL_BUS', 'School Bus', '{"category":"vehicles","emoji":"🚌","features":["takes_children_to_school","four_wheels_carries_people"]}'),
    ('VEHICLES_CAR', 'Car', '{"category":"vehicles","emoji":"🚗","features":["four_wheels_carries_people"]}'),
    ('VEHICLES_FIRE_TRUCK', 'Fire Truck', '{"category":"vehicles","emoji":"🚒","features":["four_wheels_carries_people","has_ladder_fights_fires"]}'),
    ('VEHICLES_AMBULANCE', 'Ambulance', '{"category":"vehicles","emoji":"🚑","features":["four_wheels_carries_people","takes_sick_to_hospital"]}'),
    ('VEHICLES_MOTORCYCLE', 'Motorcycle', '{"category":"vehicles","emoji":"🏍️","features":["two_wheels_motor"]}'),
    ('VEHICLES_HELICOPTER', 'Helicopter', '{"category":"vehicles","emoji":"🚁","features":["flies_in_sky","big_blades"]}'),
    ('VEHICLES_TRACTOR', 'Tractor', '{"category":"vehicles","emoji":"🚜","features":[]}'),
    ('VEHICLES_POLICE_CAR', 'Police Car', '{"category":"vehicles","emoji":"🚓","features":["four_wheels_carries_people"]}'),
    ('COLORS_BLUE', 'Blue', '{"category":"colors","emoji":"🔵","features":["sky_clear_day","night_sky"]}'),
    ('COLORS_DARK_BLUE', 'Dark Blue', '{"category":"colors","emoji":"🌃","features":["sky_clear_day","night_sky"]}'),
    ('COLORS_YELLOW', 'Yellow', '{"category":"colors","emoji":"🟡","features":["banana_color","lemon_color"]}'),
    ('COLORS_GREEN', 'Green', '{"category":"colors","emoji":"🟢","features":["grass_color"]}'),
    ('COLORS_RED', 'Red', '{"category":"colors","emoji":"🔴","features":["strawberry_color"]}'),
    ('COLORS_ORANGE', 'Orange', '{"category":"colors","emoji":"🟠","features":["orange_fruit_color","pumpkin_color"]}'),
    ('COLORS_PURPLE', 'Purple', '{"category":"colors","emoji":"🟣","features":["eggplant_color"]}'),
    ('COLORS_WHITE', 'White', '{"category":"colors","emoji":"⚪","features":["snow_color"]}'),
    ('COLORS_PINK', 'Pink', '{"category":"colors","emoji":"🌸","features":[]}'),
    ('COLORS_BROWN', 'Brown', '{"category":"colors","emoji":"🟤","features":[]}'),
    ('SHAPES_CIRCLE', 'Circle', '{"category":"shapes","emoji":"⚪","features":["is_round","no_corners"]}'),
    ('SHAPES_TRIANGLE', 'Triangle', '{"category":"shapes","emoji":"🔺","features":["three_sides"]}'),
    ('SHAPES_SQUARE', 'Square', '{"category":"shapes","emoji":"🟧","features":["four_equal_sides"]}'),
    ('SHAPES_RECTANGLE', 'Rectangle', '{"category":"shapes","emoji":"🚪","features":["looks_like_door"]}'),
    ('SHAPES_OVAL', 'Oval', '{"category":"shapes","emoji":"🥚","features":["is_round","looks_like_egg","no_corners"]}'),
    ('SHAPES_PENTAGON', 'Pentagon', '{"category":"shapes","emoji":"⬟","features":["five_sides"]}'),
    ('SHAPES_HEXAGON', 'Hexagon', '{"category":"shapes","emoji":"⬢","features":["six_sides"]}'),
    ('SHAPES_HEART', 'Heart', '{"category":"shapes","emoji":"❤️","features":["looks_like_heart"]}'),
    ('SHAPES_STAR', 'Star', '{"category":"shapes","emoji":"⭐","features":["looks_like_star"]}'),
    ('NUMBERS_ONE', 'One', '{"category":"numbers","emoji":"1️⃣","features":["count_one_apple"]}'),
    ('NUMBERS_TWO', 'Two', '{"category":"numbers","emoji":"2️⃣","features":["count_two_balls","after_one","before_three","bicycle_wheels"]}'),
    ('NUMBERS_THREE', 'Three', '{"category":"numbers","emoji":"3️⃣","features":["count_three_stars"]}'),
    ('NUMBERS_FOUR', 'Four', '{"category":"numbers","emoji":"4️⃣","features":["count_four_ducks","dog_legs"]}'),
    ('NUMBERS_FIVE', 'Five', '{"category":"numbers","emoji":"5️⃣","features":["fingers_on_hand","after_four"]}'),
    ('NUMBERS_SIX', 'Six', '{"category":"numbers","emoji":"6️⃣","features":[]}'),
    ('NUMBERS_SEVEN', 'Seven', '{"category":"numbers","emoji":"7️⃣","features":[]}'),
    ('NUMBERS_EIGHT', 'Eight', '{"category":"numbers","emoji":"8️⃣","features":[]}'),
    ('TOYS_BALL', 'Ball', '{"category":"toys","emoji":"⚽","features":["can_roll","push_around"]}'),
    ('TOYS_TEDDY_BEAR', 'Teddy Bear', '{"category":"toys","emoji":"🧸","features":["can_hug"]}'),
    ('TOYS_TOY_CAR', 'Toy Car', '{"category":"toys","emoji":"🚙","features":["can_roll","four_wheels","push_around"]}'),
    ('TOYS_BLOCKS', 'Blocks', '{"category":"toys","emoji":"🧱","features":["build_with","put_together_pieces"]}'),
    ('TOYS_TOY_GUITAR', 'Toy Guitar', '{"category":"toys","emoji":"🎸","features":["strings_instrument"]}'),
    ('TOYS_PAPER_PLANE', 'Paper Plane', '{"category":"toys","emoji":"✈️","features":["flies_when_thrown"]}'),
    ('TOYS_SPINNER', 'Spinner', '{"category":"toys","emoji":"🌀","features":["spins_around"]}'),
    ('TOYS_PUZZLE', 'Puzzle', '{"category":"toys","emoji":"🧩","features":["build_with","put_together_pieces"]}'),
    ('TOYS_DOLL', 'Doll', '{"category":"toys","emoji":"🪆","features":["can_hug","looks_like_baby"]}'),
    ('TOYS_DRUM', 'Drum', '{"category":"toys","emoji":"🥁","features":[]}'),
    ('TOYS_KITE', 'Kite', '{"category":"toys","emoji":"🪁","features":[]}'),
    ('SEA_ANIMALS_OCTOPUS', 'Octopus', '{"category":"sea_animals","emoji":"🐙","features":["eight_arms"]}'),
    ('SEA_ANIMALS_WHALE', 'Whale', '{"category":"sea_animals","emoji":"🐳","features":["very_big_ocean","jumps_out_of_water"]}'),
    ('SEA_ANIMALS_SHARK', 'Shark', '{"category":"sea_animals","emoji":"🦈","features":["sharp_teeth"]}'),
    ('SEA_ANIMALS_SEA_TURTLE', 'Sea Turtle', '{"category":"sea_animals","emoji":"🐢","features":["shell_swims_in_sea","hard_shell_slow"]}'),
    ('SEA_ANIMALS_DOLPHIN', 'Dolphin', '{"category":"sea_animals","emoji":"🐬","features":["jumps_out_of_water"]}'),
    ('SEA_ANIMALS_STARFISH', 'Starfish', '{"category":"sea_animals","emoji":"⭐","features":["five_arms"]}'),
    ('SEA_ANIMALS_JELLYFISH', 'Jellyfish', '{"category":"sea_animals","emoji":"🪼","features":["tentacles_and_stings"]}'),
    ('SEA_ANIMALS_CRAB', 'Crab', '{"category":"sea_animals","emoji":"🦀","features":["shell_swims_in_sea","claws_walks_sideways"]}'),
    ('SEA_ANIMALS_TURTLE', 'Turtle', '{"category":"sea_animals","emoji":"🐢","features":["shell_swims_in_sea","hard_shell_slow"]}'),
    ('SEA_ANIMALS_FISH', 'Fish', '{"category":"sea_animals","emoji":"🐠","features":["jumps_out_of_water","fins_and_scales"]}'),
    ('SEA_ANIMALS_SEAL', 'Seal', '{"category":"sea_animals","emoji":"🦭","features":[]}'),
    ('SEA_ANIMALS_SHRIMP', 'Shrimp', '{"category":"sea_animals","emoji":"🦐","features":[]}'),
    -- Vegetables
    ('VEGETABLES_CARROT', 'Carrot', '{"category":"vegetables","emoji":"🥕","features":["orange_long","grows_underground"]}'),
    ('VEGETABLES_BROCCOLI', 'Broccoli', '{"category":"vegetables","emoji":"🥦","features":["green_tree_shaped"]}'),
    ('VEGETABLES_TOMATO', 'Tomato', '{"category":"vegetables","emoji":"🍅","features":["red_round"]}'),
    ('VEGETABLES_CORN', 'Corn', '{"category":"vegetables","emoji":"🌽","features":["yellow_long","has_husk"]}'),
    ('VEGETABLES_POTATO', 'Potato', '{"category":"vegetables","emoji":"🥔","features":["brown_round","grows_underground"]}'),
    ('VEGETABLES_CUCUMBER', 'Cucumber', '{"category":"vegetables","emoji":"🥒","features":["green_long"]}'),
    ('VEGETABLES_PUMPKIN', 'Pumpkin', '{"category":"vegetables","emoji":"🎃","features":["orange_round","halloween_veggie"]}'),
    ('VEGETABLES_ONION', 'Onion', '{"category":"vegetables","emoji":"🧅","features":["has_layers","makes_you_cry"]}'),
    ('VEGETABLES_PEPPER', 'Pepper', '{"category":"vegetables","emoji":"🌶️","features":["red_spicy"]}'),
    ('VEGETABLES_MUSHROOM', 'Mushroom', '{"category":"vegetables","emoji":"🍄","features":["has_cap"]}'),
    ('VEGETABLES_EGGPLANT', 'Eggplant', '{"category":"vegetables","emoji":"🍆","features":[]}'),
    ('VEGETABLES_LETTUCE', 'Lettuce', '{"category":"vegetables","emoji":"🥬","features":[]}'),
    -- Body Parts
    ('BODY_PARTS_EYES', 'Eyes', '{"category":"body_parts","emoji":"👀","features":["used_to_see","on_face"]}'),
    ('BODY_PARTS_EARS', 'Ears', '{"category":"body_parts","emoji":"👂","features":["used_to_hear","on_head"]}'),
    ('BODY_PARTS_NOSE', 'Nose', '{"category":"body_parts","emoji":"👃","features":["used_to_smell","on_face"]}'),
    ('BODY_PARTS_MOUTH', 'Mouth', '{"category":"body_parts","emoji":"👄","features":["used_to_eat","used_to_talk","on_face"]}'),
    ('BODY_PARTS_HANDS', 'Hands', '{"category":"body_parts","emoji":"🤲","features":["used_to_hold","has_fingers"]}'),
    ('BODY_PARTS_FEET', 'Feet', '{"category":"body_parts","emoji":"🦶","features":["used_to_walk","has_toes"]}'),
    ('BODY_PARTS_HEAD', 'Head', '{"category":"body_parts","emoji":"🗣️","features":["on_top","has_hair"]}'),
    ('BODY_PARTS_KNEES', 'Knees', '{"category":"body_parts","emoji":"🦵","features":["used_to_bend","on_legs"]}'),
    ('BODY_PARTS_SHOULDERS', 'Shoulders', '{"category":"body_parts","emoji":"💪","features":["connects_arms"]}'),
    ('BODY_PARTS_TUMMY', 'Tummy', '{"category":"body_parts","emoji":"🫃","features":["where_food_goes"]}'),
    ('BODY_PARTS_FINGERS', 'Fingers', '{"category":"body_parts","emoji":"🖐️","features":["has_fingers","used_to_hold"]}'),
    ('BODY_PARTS_HAIR', 'Hair', '{"category":"body_parts","emoji":"💇","features":["on_top","has_hair"]}')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Questions: 10 per topic. Each has ONE correct answer item, the pool of wrong answers (same category, 3 items) and
-- correct_match (the feature the question asks for). The generator drops any wrong answer that also carries it.
-- text_vi is the Vietnamese wording of the question. The id is derived from a stable key, so re-running updates rows.
-- ---------------------------------------------------------------------------
INSERT INTO game_questions
    (id, topic_id, game_mode_id, question_text, correct_answer_item_id, difficulty, min_age, max_age, display_order,
     metadata, is_active, question_key, age_group, question_type)
SELECT md5('nimokids:question:' || q.question_key)::uuid, t.id, m.id, q.question_text, a.id,
       q.difficulty, q.min_age, q.max_age, q.display_order, q.metadata::jsonb, TRUE,
       q.question_key, q.age_group::age_group, q.question_type
FROM (VALUES
    ('ANIMALS_1', 'ANIMALS', 'What animal says "Meow"?', 'ANIMALS_CAT', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"says_meow"},"text_vi":"Con vật nào kêu Meo meo?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('ANIMALS_2', 'ANIMALS', 'What animal says "Woof"?', 'ANIMALS_DOG', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"says_woof"},"text_vi":"Con vật nào kêu Gâu gâu?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('ANIMALS_3', 'ANIMALS', 'What animal has a big trunk?', 'ANIMALS_ELEPHANT', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"long_trunk"},"text_vi":"Con vật nào có cái vòi to?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('ANIMALS_4', 'ANIMALS', 'What animal has a very long neck?', 'ANIMALS_GIRAFFE', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"long_neck"},"text_vi":"Con vật nào có cổ rất dài?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('ANIMALS_5', 'ANIMALS', 'What animal has black and white stripes?', 'ANIMALS_ZEBRA', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"black_white_stripes"},"text_vi":"Con vật nào có vạch đen trắng?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('ANIMALS_6', 'ANIMALS', 'What animal is the king of the jungle?', 'ANIMALS_LION', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"has_mane"},"text_vi":"Con vật nào là chúa tể rừng xanh?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('ANIMALS_7', 'ANIMALS', 'What animal loves to hop and hop?', 'ANIMALS_RABBIT', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"can_hop"},"text_vi":"Con vật nào thích nhảy nhảy?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('ANIMALS_8', 'ANIMALS', 'What animal gives us milk?', 'ANIMALS_COW', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"gives_milk"},"text_vi":"Con vật nào cho chúng ta uống sữa?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('ANIMALS_9', 'ANIMALS', 'What animal carries its home on its back?', 'ANIMALS_TURTLE', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"has_shell"},"text_vi":"Con vật nào mang nhà trên lưng?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('ANIMALS_10', 'ANIMALS', 'What animal loves to swing on trees?', 'ANIMALS_MONKEY', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"animals"},"count":3}],"correct_match":{"features":"swings_from_trees"},"text_vi":"Con vật nào thích đu cây?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('FRUITS_1', 'FRUITS', 'What fruit is yellow and long?', 'FRUITS_BANANA', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"yellow_long"},"text_vi":"Quả nào màu vàng và dài?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('FRUITS_2', 'FRUITS', 'What fruit is round and red?', 'FRUITS_APPLE', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"red_round"},"text_vi":"Quả nào tròn và màu đỏ?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('FRUITS_3', 'FRUITS', 'What fruit is orange and juicy?', 'FRUITS_ORANGE', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"orange_round"},"text_vi":"Quả nào màu cam và nhiều nước?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('FRUITS_4', 'FRUITS', 'What fruit grows in a big bunch?', 'FRUITS_GRAPE', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"grows_in_bunch"},"text_vi":"Quả nào mọc thành chùm to?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('FRUITS_5', 'FRUITS', 'What fruit is green outside and red inside?', 'FRUITS_WATERMELON', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"green_outside_red_inside"},"text_vi":"Quả nào xanh bên ngoài, đỏ bên trong?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('FRUITS_6', 'FRUITS', 'What fruit has a spiky top?', 'FRUITS_PINEAPPLE', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"has_crown"},"text_vi":"Quả nào có lá gai trên đỉnh?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('FRUITS_7', 'FRUITS', 'What tiny red fruit has seeds outside?', 'FRUITS_STRAWBERRY', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"seeds_outside"},"text_vi":"Quả đỏ nhỏ nào có hạt ở ngoài vỏ?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('FRUITS_8', 'FRUITS', 'What fruit looks like a light bulb?', 'FRUITS_PEAR', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"pear_shaped"},"text_vi":"Quả nào có hình giống bóng đèn?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('FRUITS_9', 'FRUITS', 'What fruit is fuzzy outside and green inside?', 'FRUITS_KIWI', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"hairy_green_inside"},"text_vi":"Quả nào có vỏ xù và ruột xanh?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('FRUITS_10', 'FRUITS', 'What small purple fruit has a pit inside?', 'FRUITS_PLUM', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"fruits"},"count":3}],"correct_match":{"features":"big_pit_purple_or_yellow"},"text_vi":"Quả tím nhỏ nào có hạt to bên trong?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('VEHICLES_1', 'VEHICLES', 'What flies up in the sky?', 'VEHICLES_AIRPLANE', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"flies_in_sky"},"text_vi":"Cái gì bay trên bầu trời?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('VEHICLES_2', 'VEHICLES', 'What goes on the water?', 'VEHICLES_BOAT', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"travels_on_water"},"text_vi":"Cái gì đi trên mặt nước?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('VEHICLES_3', 'VEHICLES', 'What vehicle goes "Choo choo"?', 'VEHICLES_TRAIN', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"goes_choo_choo"},"text_vi":"Phương tiện nào kêu Choo choo?"}', 'AGE_1_3', 'SOUND_TO_ANIMAL'),
    ('VEHICLES_4', 'VEHICLES', 'What has two wheels and pedals?', 'VEHICLES_BICYCLE', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"two_wheels_pedals"},"text_vi":"Cái gì có hai bánh và bàn đạp?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('VEHICLES_5', 'VEHICLES', 'What big yellow bus takes kids to school?', 'VEHICLES_SCHOOL_BUS', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"takes_children_to_school"},"text_vi":"Xe bus vàng to nào chở trẻ em đến trường?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('VEHICLES_6', 'VEHICLES', 'What has four wheels and takes you places?', 'VEHICLES_CAR', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"four_wheels_carries_people"},"text_vi":"Cái gì có bốn bánh và đưa bạn đến nơi bạn muốn?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('VEHICLES_7', 'VEHICLES', 'What big red truck fights fires?', 'VEHICLES_FIRE_TRUCK', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"has_ladder_fights_fires"},"text_vi":"Xe tải đỏ to nào dùng để chữa cháy?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('VEHICLES_8', 'VEHICLES', 'What helps sick people go to the hospital?', 'VEHICLES_AMBULANCE', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"takes_sick_to_hospital"},"text_vi":"Cái gì đưa người bệnh đến bệnh viện?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('VEHICLES_9', 'VEHICLES', 'What has two wheels and goes vroom vroom?', 'VEHICLES_MOTORCYCLE', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"two_wheels_motor"},"text_vi":"Cái gì có hai bánh và kêu vù vù?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('VEHICLES_10', 'VEHICLES', 'What flies and has big spinning blades?', 'VEHICLES_HELICOPTER', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"vehicles"},"count":3}],"correct_match":{"features":"big_blades"},"text_vi":"Cái gì bay và có cánh quạt to quay?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('COLORS_1', 'COLORS', 'What color is the sky?', 'COLORS_BLUE', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"sky_clear_day"},"text_vi":"Bầu trời có màu gì?"}', 'AGE_1_3', 'COLOR_RECOGNITION'),
    ('COLORS_2', 'COLORS', 'What color is a banana?', 'COLORS_YELLOW', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"banana_color"},"text_vi":"Quả chuối có màu gì?"}', 'AGE_1_3', 'COLOR_RECOGNITION'),
    ('COLORS_3', 'COLORS', 'What color is grass?', 'COLORS_GREEN', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"grass_color"},"text_vi":"Cỏ có màu gì?"}', 'AGE_1_3', 'COLOR_RECOGNITION'),
    ('COLORS_4', 'COLORS', 'What color is an apple?', 'COLORS_RED', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"strawberry_color"},"text_vi":"Quả táo có màu gì?"}', 'AGE_1_3', 'COLOR_RECOGNITION'),
    ('COLORS_5', 'COLORS', 'What color is an orange?', 'COLORS_ORANGE', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"orange_fruit_color"},"text_vi":"Quả cam có màu gì?"}', 'AGE_1_3', 'COLOR_RECOGNITION'),
    ('COLORS_6', 'COLORS', 'What color is a grape?', 'COLORS_PURPLE', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"eggplant_color"},"text_vi":"Quả nho có màu gì?"}', 'AGE_1_3', 'COLOR_RECOGNITION'),
    ('COLORS_7', 'COLORS', 'What color is snow?', 'COLORS_WHITE', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"snow_color"},"text_vi":"Tuyết có màu gì?"}', 'AGE_1_3', 'COLOR_RECOGNITION'),
    ('COLORS_8', 'COLORS', 'What color is a pumpkin?', 'COLORS_ORANGE', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"pumpkin_color"},"text_vi":"Quả bí có màu gì?"}', 'AGE_1_3', 'COLOR_RECOGNITION'),
    ('COLORS_9', 'COLORS', 'What color is a lemon?', 'COLORS_YELLOW', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"lemon_color"},"text_vi":"Quả chanh có màu gì?"}', 'AGE_1_3', 'COLOR_RECOGNITION'),
    ('COLORS_10', 'COLORS', 'What color is the night sky?', 'COLORS_DARK_BLUE', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"colors"},"count":3}],"correct_match":{"features":"night_sky"},"text_vi":"Bầu trời ban đêm có màu gì?"}', 'AGE_1_3', 'COLOR_RECOGNITION'),
    ('SHAPES_1', 'SHAPES', 'What shape is perfectly round?', 'SHAPES_CIRCLE', 1, 2, 5, 1, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"is_round"},"text_vi":"Hình nào tròn hoàn toàn?"}', 'AGE_1_3', 'SHAPE_RECOGNITION'),
    ('SHAPES_2', 'SHAPES', 'What shape has three sides?', 'SHAPES_TRIANGLE', 1, 2, 5, 2, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"three_sides"},"text_vi":"Hình nào có ba cạnh?"}', 'AGE_1_3', 'SHAPE_RECOGNITION'),
    ('SHAPES_3', 'SHAPES', 'What shape has four equal sides?', 'SHAPES_SQUARE', 1, 2, 5, 3, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"four_equal_sides"},"text_vi":"Hình nào có bốn cạnh bằng nhau?"}', 'AGE_1_3', 'SHAPE_RECOGNITION'),
    ('SHAPES_4', 'SHAPES', 'What shape looks like a door?', 'SHAPES_RECTANGLE', 1, 2, 5, 4, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"looks_like_door"},"text_vi":"Hình nào trông như cái cửa?"}', 'AGE_1_3', 'SHAPE_RECOGNITION'),
    ('SHAPES_5', 'SHAPES', 'What shape looks like an egg?', 'SHAPES_OVAL', 1, 2, 5, 5, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"looks_like_egg"},"text_vi":"Hình nào trông như quả trứng?"}', 'AGE_1_3', 'SHAPE_RECOGNITION'),
    ('SHAPES_6', 'SHAPES', 'What shape has five sides?', 'SHAPES_PENTAGON', 2, 2, 5, 6, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"five_sides"},"text_vi":"Hình nào có năm cạnh?"}', 'AGE_1_3', 'SHAPE_RECOGNITION'),
    ('SHAPES_7', 'SHAPES', 'What shape has six sides?', 'SHAPES_HEXAGON', 2, 2, 5, 7, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"six_sides"},"text_vi":"Hình nào có sáu cạnh?"}', 'AGE_1_3', 'SHAPE_RECOGNITION'),
    ('SHAPES_8', 'SHAPES', 'What shape looks like a heart?', 'SHAPES_HEART', 2, 2, 5, 8, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"looks_like_heart"},"text_vi":"Hình nào trông như trái tim?"}', 'AGE_1_3', 'SHAPE_RECOGNITION'),
    ('SHAPES_9', 'SHAPES', 'What shape looks like a star?', 'SHAPES_STAR', 2, 2, 5, 9, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"looks_like_star"},"text_vi":"Hình nào trông như ngôi sao?"}', 'AGE_1_3', 'SHAPE_RECOGNITION'),
    ('SHAPES_10', 'SHAPES', 'What shape has no corners at all?', 'SHAPES_CIRCLE', 2, 2, 5, 10, '{"distractor_rules":[{"match":{"category":"shapes"},"count":3}],"correct_match":{"features":"no_corners"},"text_vi":"Hình nào không có góc nào cả?"}', 'AGE_1_3', 'SHAPE_RECOGNITION'),
    ('NUMBERS_1', 'NUMBERS', 'How many apples? 🍎', 'NUMBERS_ONE', 1, 2, 5, 1, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"count_one_apple"},"text_vi":"Có mấy quả táo?"}', 'AGE_1_3', 'COUNTING'),
    ('NUMBERS_2', 'NUMBERS', 'How many balls? ⚽⚽', 'NUMBERS_TWO', 1, 2, 5, 2, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"count_two_balls"},"text_vi":"Có mấy quả bóng?"}', 'AGE_1_3', 'COUNTING'),
    ('NUMBERS_3', 'NUMBERS', 'How many stars? ⭐⭐⭐', 'NUMBERS_THREE', 1, 2, 5, 3, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"count_three_stars"},"text_vi":"Có mấy ngôi sao?"}', 'AGE_1_3', 'COUNTING'),
    ('NUMBERS_4', 'NUMBERS', 'How many ducks? 🦆🦆🦆🦆', 'NUMBERS_FOUR', 1, 2, 5, 4, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"count_four_ducks"},"text_vi":"Có mấy con vịt?"}', 'AGE_1_3', 'COUNTING'),
    ('NUMBERS_5', 'NUMBERS', 'How many fingers on one hand?', 'NUMBERS_FIVE', 1, 2, 5, 5, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"fingers_on_hand"},"text_vi":"Một bàn tay có mấy ngón?"}', 'AGE_1_3', 'COUNTING'),
    ('NUMBERS_6', 'NUMBERS', 'What number comes after 1?', 'NUMBERS_TWO', 2, 2, 5, 6, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"after_one"},"text_vi":"Số nào đứng sau số 1?"}', 'AGE_1_3', 'SEQUENCE'),
    ('NUMBERS_7', 'NUMBERS', 'What number comes after 4?', 'NUMBERS_FIVE', 2, 2, 5, 7, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"after_four"},"text_vi":"Số nào đứng sau số 4?"}', 'AGE_1_3', 'SEQUENCE'),
    ('NUMBERS_8', 'NUMBERS', 'What number comes before 3?', 'NUMBERS_TWO', 2, 2, 5, 8, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"before_three"},"text_vi":"Số nào đứng trước số 3?"}', 'AGE_1_3', 'SEQUENCE'),
    ('NUMBERS_9', 'NUMBERS', 'How many wheels on a bicycle?', 'NUMBERS_TWO', 2, 2, 5, 9, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"bicycle_wheels"},"text_vi":"Xe đạp có mấy bánh?"}', 'AGE_1_3', 'COUNTING'),
    ('NUMBERS_10', 'NUMBERS', 'How many legs does a dog have?', 'NUMBERS_FOUR', 2, 2, 5, 10, '{"distractor_rules":[{"match":{"category":"numbers"},"count":3}],"correct_match":{"features":"dog_legs"},"text_vi":"Con chó có mấy chân?"}', 'AGE_1_3', 'COUNTING'),
    ('TOYS_1', 'TOYS', 'What round toy can you roll and kick?', 'TOYS_BALL', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"can_roll"},"text_vi":"Đồ chơi tròn nào bạn có thể lăn và đá?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('TOYS_2', 'TOYS', 'What soft toy can you hug?', 'TOYS_TEDDY_BEAR', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"can_hug"},"text_vi":"Đồ chơi mềm nào bạn có thể ôm?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('TOYS_3', 'TOYS', 'What toy has four wheels and goes zoom?', 'TOYS_TOY_CAR', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"four_wheels"},"text_vi":"Đồ chơi nào có bốn bánh và chạy vèo vèo?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('TOYS_4', 'TOYS', 'What toy do you stack to build things?', 'TOYS_BLOCKS', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"build_with"},"text_vi":"Đồ chơi nào bạn xếp chồng để xây?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('TOYS_5', 'TOYS', 'What toy has strings and makes music?', 'TOYS_TOY_GUITAR', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"strings_instrument"},"text_vi":"Đồ chơi nào có dây và tạo ra âm nhạc?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('TOYS_6', 'TOYS', 'What toy can you push across the floor?', 'TOYS_TOY_CAR', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"push_around"},"text_vi":"Đồ chơi nào bạn có thể đẩy trên sàn?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('TOYS_7', 'TOYS', 'What toy flies when you throw it?', 'TOYS_PAPER_PLANE', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"flies_when_thrown"},"text_vi":"Đồ chơi nào bay khi bạn ném?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('TOYS_8', 'TOYS', 'What toy spins round and round?', 'TOYS_SPINNER', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"spins_around"},"text_vi":"Đồ chơi nào xoay tròn mãi?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('TOYS_9', 'TOYS', 'What toy has many pieces to fit together?', 'TOYS_PUZZLE', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"put_together_pieces"},"text_vi":"Đồ chơi nào có nhiều mảnh để ghép lại?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('TOYS_10', 'TOYS', 'What toy looks like a little baby?', 'TOYS_DOLL', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"toys"},"count":3}],"correct_match":{"features":"looks_like_baby"},"text_vi":"Đồ chơi nào trông như em bé nhỏ?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('SEA_ANIMALS_1', 'SEA_ANIMALS', 'What sea animal has eight arms?', 'SEA_ANIMALS_OCTOPUS', 1, 2, 5, 1, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"eight_arms"},"text_vi":"Con vật biển nào có tám cánh tay?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('SEA_ANIMALS_2', 'SEA_ANIMALS', 'What is the biggest animal in the ocean?', 'SEA_ANIMALS_WHALE', 1, 2, 5, 2, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"very_big_ocean"},"text_vi":"Con vật nào to nhất dưới biển?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('SEA_ANIMALS_3', 'SEA_ANIMALS', 'What ocean animal has very sharp teeth?', 'SEA_ANIMALS_SHARK', 1, 2, 5, 3, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"sharp_teeth"},"text_vi":"Con vật biển nào có răng rất sắc?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('SEA_ANIMALS_4', 'SEA_ANIMALS', 'What sea animal has a hard shell?', 'SEA_ANIMALS_SEA_TURTLE', 1, 2, 5, 4, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"shell_swims_in_sea"},"text_vi":"Con vật biển nào có mai cứng?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('SEA_ANIMALS_5', 'SEA_ANIMALS', 'What friendly sea animal loves to jump?', 'SEA_ANIMALS_DOLPHIN', 1, 2, 5, 5, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"jumps_out_of_water"},"text_vi":"Con vật biển thân thiện nào thích nhảy?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('SEA_ANIMALS_6', 'SEA_ANIMALS', 'What sea animal looks like a star?', 'SEA_ANIMALS_STARFISH', 2, 2, 5, 6, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"five_arms"},"text_vi":"Con vật biển nào trông như ngôi sao?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('SEA_ANIMALS_7', 'SEA_ANIMALS', 'What sea animal looks like a floating umbrella?', 'SEA_ANIMALS_JELLYFISH', 2, 2, 5, 7, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"tentacles_and_stings"},"text_vi":"Con vật biển nào trông như cái ô đang nổi?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('SEA_ANIMALS_8', 'SEA_ANIMALS', 'What sea animal has big claws and walks sideways?', 'SEA_ANIMALS_CRAB', 2, 2, 5, 8, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"claws_walks_sideways"},"text_vi":"Con vật biển nào có càng to và đi ngang?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('SEA_ANIMALS_9', 'SEA_ANIMALS', 'What animal swims slowly with a shell on its back?', 'SEA_ANIMALS_TURTLE', 2, 2, 5, 9, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"hard_shell_slow"},"text_vi":"Con vật nào bơi chậm và có mai trên lưng?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('SEA_ANIMALS_10', 'SEA_ANIMALS', 'What animal lives in water and has fins?', 'SEA_ANIMALS_FISH', 2, 2, 5, 10, '{"distractor_rules":[{"match":{"category":"sea_animals"},"count":3}],"correct_match":{"features":"fins_and_scales"},"text_vi":"Con vật nào sống dưới nước và có vây?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    -- Vegetables
    ('VEGETABLES_1', 'VEGETABLES', 'What vegetable is orange and long?', 'VEGETABLES_CARROT', 1, 2, 5, 1, '{"distractor_rules":[{"match":{"category":"vegetables"},"count":3}],"correct_match":{"features":"orange_long"},"text_vi":"Rau củ nào màu cam và dài?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('VEGETABLES_2', 'VEGETABLES', 'What vegetable looks like a little tree?', 'VEGETABLES_BROCCOLI', 1, 2, 5, 2, '{"distractor_rules":[{"match":{"category":"vegetables"},"count":3}],"correct_match":{"features":"green_tree_shaped"},"text_vi":"Rau nào trông giống cây nhỏ?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('VEGETABLES_3', 'VEGETABLES', 'What vegetable is red and round?', 'VEGETABLES_TOMATO', 1, 2, 5, 3, '{"distractor_rules":[{"match":{"category":"vegetables"},"count":3}],"correct_match":{"features":"red_round"},"text_vi":"Rau củ nào đỏ và tròn?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('VEGETABLES_4', 'VEGETABLES', 'What vegetable is yellow and has a husk?', 'VEGETABLES_CORN', 1, 2, 5, 4, '{"distractor_rules":[{"match":{"category":"vegetables"},"count":3}],"correct_match":{"features":"has_husk"},"text_vi":"Rau củ nào màu vàng và có vỏ bọc?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('VEGETABLES_5', 'VEGETABLES', 'What vegetable grows under the ground?', 'VEGETABLES_POTATO', 1, 2, 5, 5, '{"distractor_rules":[{"match":{"category":"vegetables"},"count":3}],"correct_match":{"features":"grows_underground"},"text_vi":"Củ nào mọc dưới đất?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('VEGETABLES_6', 'VEGETABLES', 'What vegetable is green and long?', 'VEGETABLES_CUCUMBER', 2, 2, 5, 6, '{"distractor_rules":[{"match":{"category":"vegetables"},"count":3}],"correct_match":{"features":"green_long"},"text_vi":"Rau nào màu xanh và dài?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('VEGETABLES_7', 'VEGETABLES', 'What big orange vegetable do we see on Halloween?', 'VEGETABLES_PUMPKIN', 2, 2, 5, 7, '{"distractor_rules":[{"match":{"category":"vegetables"},"count":3}],"correct_match":{"features":"halloween_veggie"},"text_vi":"Quả bí cam to nào xuất hiện vào Halloween?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('VEGETABLES_8', 'VEGETABLES', 'What vegetable makes you cry when you cut it?', 'VEGETABLES_ONION', 2, 2, 5, 8, '{"distractor_rules":[{"match":{"category":"vegetables"},"count":3}],"correct_match":{"features":"makes_you_cry"},"text_vi":"Củ nào làm bạn khóc khi cắt?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('VEGETABLES_9', 'VEGETABLES', 'What vegetable is red and spicy?', 'VEGETABLES_PEPPER', 2, 2, 5, 9, '{"distractor_rules":[{"match":{"category":"vegetables"},"count":3}],"correct_match":{"features":"red_spicy"},"text_vi":"Rau nào màu đỏ và cay?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('VEGETABLES_10', 'VEGETABLES', 'What vegetable has a little cap on top?', 'VEGETABLES_MUSHROOM', 2, 2, 5, 10, '{"distractor_rules":[{"match":{"category":"vegetables"},"count":3}],"correct_match":{"features":"has_cap"},"text_vi":"Loại nào có cái mũ nhỏ trên đỉnh?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    -- Body Parts
    ('BODY_PARTS_1', 'BODY_PARTS', 'What do we use to see?', 'BODY_PARTS_EYES', 1, 1, 5, 1, '{"distractor_rules":[{"match":{"category":"body_parts"},"count":3}],"correct_match":{"features":"used_to_see"},"text_vi":"Chúng ta dùng gì để nhìn?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('BODY_PARTS_2', 'BODY_PARTS', 'What do we use to hear?', 'BODY_PARTS_EARS', 1, 1, 5, 2, '{"distractor_rules":[{"match":{"category":"body_parts"},"count":3}],"correct_match":{"features":"used_to_hear"},"text_vi":"Chúng ta dùng gì để nghe?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('BODY_PARTS_3', 'BODY_PARTS', 'What do we use to smell flowers?', 'BODY_PARTS_NOSE', 1, 1, 5, 3, '{"distractor_rules":[{"match":{"category":"body_parts"},"count":3}],"correct_match":{"features":"used_to_smell"},"text_vi":"Chúng ta dùng gì để ngửi hoa?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('BODY_PARTS_4', 'BODY_PARTS', 'What do we use to eat and talk?', 'BODY_PARTS_MOUTH', 1, 1, 5, 4, '{"distractor_rules":[{"match":{"category":"body_parts"},"count":3}],"correct_match":{"features":"used_to_eat"},"text_vi":"Chúng ta dùng gì để ăn và nói?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('BODY_PARTS_5', 'BODY_PARTS', 'What do we use to hold things?', 'BODY_PARTS_HANDS', 1, 1, 5, 5, '{"distractor_rules":[{"match":{"category":"body_parts"},"count":3}],"correct_match":{"features":"used_to_hold"},"text_vi":"Chúng ta dùng gì để cầm đồ?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('BODY_PARTS_6', 'BODY_PARTS', 'What do we use to walk?', 'BODY_PARTS_FEET', 2, 1, 5, 6, '{"distractor_rules":[{"match":{"category":"body_parts"},"count":3}],"correct_match":{"features":"used_to_walk"},"text_vi":"Chúng ta dùng gì để đi bộ?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('BODY_PARTS_7', 'BODY_PARTS', 'What is on the very top of your body?', 'BODY_PARTS_HEAD', 2, 1, 5, 7, '{"distractor_rules":[{"match":{"category":"body_parts"},"count":3}],"correct_match":{"features":"on_top"},"text_vi":"Phần nào ở trên đỉnh cơ thể?"}', 'AGE_1_3', 'IMAGE_TO_WORD'),
    ('BODY_PARTS_8', 'BODY_PARTS', 'What body part helps us bend our legs?', 'BODY_PARTS_KNEES', 2, 1, 5, 8, '{"distractor_rules":[{"match":{"category":"body_parts"},"count":3}],"correct_match":{"features":"used_to_bend"},"text_vi":"Bộ phận nào giúp chúng ta gập chân?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('BODY_PARTS_9', 'BODY_PARTS', 'What connects your arms to your body?', 'BODY_PARTS_SHOULDERS', 2, 1, 5, 9, '{"distractor_rules":[{"match":{"category":"body_parts"},"count":3}],"correct_match":{"features":"connects_arms"},"text_vi":"Bộ phận nào nối cánh tay với cơ thể?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING'),
    ('BODY_PARTS_10', 'BODY_PARTS', 'Where does your food go after you eat?', 'BODY_PARTS_TUMMY', 2, 1, 5, 10, '{"distractor_rules":[{"match":{"category":"body_parts"},"count":3}],"correct_match":{"features":"where_food_goes"},"text_vi":"Thức ăn đi đâu sau khi ăn?"}', 'AGE_1_3', 'ATTRIBUTE_MATCHING')
) AS q(question_key, topic_code, question_text, correct_code, difficulty, min_age, max_age, display_order, metadata, age_group, question_type)
JOIN topics t ON t.code = q.topic_code
JOIN answer_items a ON a.code = q.correct_code
CROSS JOIN game_modes m
WHERE m.code = 'GUESS'
ON CONFLICT (id) DO UPDATE
    SET topic_id = EXCLUDED.topic_id, question_text = EXCLUDED.question_text,
        correct_answer_item_id = EXCLUDED.correct_answer_item_id, difficulty = EXCLUDED.difficulty,
        min_age = EXCLUDED.min_age, max_age = EXCLUDED.max_age, display_order = EXCLUDED.display_order,
        metadata = EXCLUDED.metadata, question_key = EXCLUDED.question_key,
        age_group = EXCLUDED.age_group, question_type = EXCLUDED.question_type, is_active = TRUE;

-- ---------------------------------------------------------------------------
-- Clean-up of the first dev content (4 roots with sub-topics and its own vocabulary). Rows already used by a game
-- session are kept but switched off, so history stays intact.
-- ---------------------------------------------------------------------------
UPDATE game_questions SET is_active = FALSE
WHERE id IN (
    md5('nimokids:question:' || 'FLY_1')::uuid,
    md5('nimokids:question:' || 'FLY_2')::uuid,
    md5('nimokids:question:' || 'FLY_3')::uuid,
    md5('nimokids:question:' || 'FLY_4')::uuid,
    md5('nimokids:question:' || 'FLY_5')::uuid,
    md5('nimokids:question:' || 'FARM_1')::uuid,
    md5('nimokids:question:' || 'FARM_2')::uuid,
    md5('nimokids:question:' || 'FARM_3')::uuid,
    md5('nimokids:question:' || 'FARM_4')::uuid,
    md5('nimokids:question:' || 'FARM_5')::uuid,
    md5('nimokids:question:' || 'WATER_1')::uuid,
    md5('nimokids:question:' || 'WATER_2')::uuid,
    md5('nimokids:question:' || 'WATER_3')::uuid,
    md5('nimokids:question:' || 'WATER_4')::uuid,
    md5('nimokids:question:' || 'WATER_5')::uuid,
    md5('nimokids:question:' || 'FRUIT_1')::uuid,
    md5('nimokids:question:' || 'FRUIT_2')::uuid,
    md5('nimokids:question:' || 'FRUIT_3')::uuid,
    md5('nimokids:question:' || 'FRUIT_4')::uuid,
    md5('nimokids:question:' || 'FRUIT_5')::uuid,
    md5('nimokids:question:' || 'VEG_1')::uuid,
    md5('nimokids:question:' || 'VEG_2')::uuid,
    md5('nimokids:question:' || 'VEG_3')::uuid,
    md5('nimokids:question:' || 'VEG_4')::uuid,
    md5('nimokids:question:' || 'VEG_5')::uuid,
    md5('nimokids:question:' || 'AIR_1')::uuid,
    md5('nimokids:question:' || 'AIR_2')::uuid,
    md5('nimokids:question:' || 'AIR_3')::uuid,
    md5('nimokids:question:' || 'AIR_4')::uuid,
    md5('nimokids:question:' || 'AIR_5')::uuid,
    md5('nimokids:question:' || 'SEA_1')::uuid,
    md5('nimokids:question:' || 'SEA_2')::uuid,
    md5('nimokids:question:' || 'SEA_3')::uuid,
    md5('nimokids:question:' || 'SEA_4')::uuid,
    md5('nimokids:question:' || 'SEA_5')::uuid,
    md5('nimokids:question:' || 'MATH_1')::uuid,
    md5('nimokids:question:' || 'MATH_2')::uuid,
    md5('nimokids:question:' || 'MATH_3')::uuid,
    md5('nimokids:question:' || 'MATH_4')::uuid,
    md5('nimokids:question:' || 'MATH_5')::uuid);

DELETE FROM game_questions q
WHERE q.is_active = FALSE
  AND q.id IN (
    md5('nimokids:question:' || 'FLY_1')::uuid,
    md5('nimokids:question:' || 'FLY_2')::uuid,
    md5('nimokids:question:' || 'FLY_3')::uuid,
    md5('nimokids:question:' || 'FLY_4')::uuid,
    md5('nimokids:question:' || 'FLY_5')::uuid,
    md5('nimokids:question:' || 'FARM_1')::uuid,
    md5('nimokids:question:' || 'FARM_2')::uuid,
    md5('nimokids:question:' || 'FARM_3')::uuid,
    md5('nimokids:question:' || 'FARM_4')::uuid,
    md5('nimokids:question:' || 'FARM_5')::uuid,
    md5('nimokids:question:' || 'WATER_1')::uuid,
    md5('nimokids:question:' || 'WATER_2')::uuid,
    md5('nimokids:question:' || 'WATER_3')::uuid,
    md5('nimokids:question:' || 'WATER_4')::uuid,
    md5('nimokids:question:' || 'WATER_5')::uuid,
    md5('nimokids:question:' || 'FRUIT_1')::uuid,
    md5('nimokids:question:' || 'FRUIT_2')::uuid,
    md5('nimokids:question:' || 'FRUIT_3')::uuid,
    md5('nimokids:question:' || 'FRUIT_4')::uuid,
    md5('nimokids:question:' || 'FRUIT_5')::uuid,
    md5('nimokids:question:' || 'VEG_1')::uuid,
    md5('nimokids:question:' || 'VEG_2')::uuid,
    md5('nimokids:question:' || 'VEG_3')::uuid,
    md5('nimokids:question:' || 'VEG_4')::uuid,
    md5('nimokids:question:' || 'VEG_5')::uuid,
    md5('nimokids:question:' || 'AIR_1')::uuid,
    md5('nimokids:question:' || 'AIR_2')::uuid,
    md5('nimokids:question:' || 'AIR_3')::uuid,
    md5('nimokids:question:' || 'AIR_4')::uuid,
    md5('nimokids:question:' || 'AIR_5')::uuid,
    md5('nimokids:question:' || 'SEA_1')::uuid,
    md5('nimokids:question:' || 'SEA_2')::uuid,
    md5('nimokids:question:' || 'SEA_3')::uuid,
    md5('nimokids:question:' || 'SEA_4')::uuid,
    md5('nimokids:question:' || 'SEA_5')::uuid,
    md5('nimokids:question:' || 'MATH_1')::uuid,
    md5('nimokids:question:' || 'MATH_2')::uuid,
    md5('nimokids:question:' || 'MATH_3')::uuid,
    md5('nimokids:question:' || 'MATH_4')::uuid,
    md5('nimokids:question:' || 'MATH_5')::uuid)
  AND NOT EXISTS (SELECT 1 FROM session_questions sq WHERE sq.question_id = q.id);

UPDATE answer_items SET is_active = FALSE WHERE code IN ('FOOD_FRUITS', 'ANIMAL_CAT', 'ANIMAL_DOG', 'ANIMAL_RABBIT', 'ANIMAL_COW', 'ANIMAL_PIG', 'ANIMAL_SHEEP', 'ANIMAL_HORSE', 'ANIMAL_GOAT', 'ANIMAL_ELEPHANT', 'ANIMAL_LION', 'ANIMAL_MONKEY', 'ANIMAL_BEAR', 'ANIMAL_TIGER', 'ANIMAL_FISH', 'ANIMAL_WHALE', 'ANIMAL_DOLPHIN', 'ANIMAL_CRAB', 'ANIMAL_OCTOPUS', 'ANIMAL_TURTLE', 'ANIMAL_BIRD', 'ANIMAL_EAGLE', 'ANIMAL_OWL', 'ANIMAL_BUTTERFLY', 'ANIMAL_BEE', 'FOOD_APPLE', 'FOOD_BANANA', 'FOOD_STRAWBERRY', 'FOOD_ORANGE', 'FOOD_GRAPES', 'FOOD_CARROT', 'FOOD_BROCCOLI', 'FOOD_POTATO', 'FOOD_CORN', 'FOOD_CUCUMBER', 'VEHICLE_CAR', 'VEHICLE_BUS', 'VEHICLE_BICYCLE', 'VEHICLE_TRAIN', 'VEHICLE_TRUCK', 'VEHICLE_AIRPLANE', 'VEHICLE_HELICOPTER', 'VEHICLE_ROCKET', 'VEHICLE_BALLOON', 'VEHICLE_DRONE', 'VEHICLE_BOAT', 'VEHICLE_SHIP', 'VEHICLE_SUBMARINE', 'VEHICLE_CANOE', 'VEHICLE_FERRY', 'NUMBER_1', 'NUMBER_2', 'NUMBER_3', 'NUMBER_4', 'NUMBER_5', 'NUMBER_6', 'NUMBER_7', 'NUMBER_8', 'NUMBER_9');

DELETE FROM answer_items a
WHERE a.code IN ('FOOD_FRUITS', 'ANIMAL_CAT', 'ANIMAL_DOG', 'ANIMAL_RABBIT', 'ANIMAL_COW', 'ANIMAL_PIG', 'ANIMAL_SHEEP', 'ANIMAL_HORSE', 'ANIMAL_GOAT', 'ANIMAL_ELEPHANT', 'ANIMAL_LION', 'ANIMAL_MONKEY', 'ANIMAL_BEAR', 'ANIMAL_TIGER', 'ANIMAL_FISH', 'ANIMAL_WHALE', 'ANIMAL_DOLPHIN', 'ANIMAL_CRAB', 'ANIMAL_OCTOPUS', 'ANIMAL_TURTLE', 'ANIMAL_BIRD', 'ANIMAL_EAGLE', 'ANIMAL_OWL', 'ANIMAL_BUTTERFLY', 'ANIMAL_BEE', 'FOOD_APPLE', 'FOOD_BANANA', 'FOOD_STRAWBERRY', 'FOOD_ORANGE', 'FOOD_GRAPES', 'FOOD_CARROT', 'FOOD_BROCCOLI', 'FOOD_POTATO', 'FOOD_CORN', 'FOOD_CUCUMBER', 'VEHICLE_CAR', 'VEHICLE_BUS', 'VEHICLE_BICYCLE', 'VEHICLE_TRAIN', 'VEHICLE_TRUCK', 'VEHICLE_AIRPLANE', 'VEHICLE_HELICOPTER', 'VEHICLE_ROCKET', 'VEHICLE_BALLOON', 'VEHICLE_DRONE', 'VEHICLE_BOAT', 'VEHICLE_SHIP', 'VEHICLE_SUBMARINE', 'VEHICLE_CANOE', 'VEHICLE_FERRY', 'NUMBER_1', 'NUMBER_2', 'NUMBER_3', 'NUMBER_4', 'NUMBER_5', 'NUMBER_6', 'NUMBER_7', 'NUMBER_8', 'NUMBER_9')
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.correct_answer_item_id = a.id);

UPDATE topics SET is_active = FALSE WHERE code IN ('ANIMALS_FLY', 'ANIMALS_FARM', 'ANIMALS_WATER', 'FOOD_FRUITS', 'FOOD_VEGETABLES', 'VEHICLES_AIR', 'VEHICLES_WATER');

DELETE FROM topics t
WHERE t.code IN ('ANIMALS_FLY', 'ANIMALS_FARM', 'ANIMALS_WATER', 'FOOD_FRUITS', 'FOOD_VEGETABLES', 'VEHICLES_AIR', 'VEHICLES_WATER')
  AND t.parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.topic_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM game_sessions s WHERE s.topic_id = t.id);

DELETE FROM topics t
WHERE t.code IN ('ANIMALS_FLY', 'ANIMALS_FARM', 'ANIMALS_WATER', 'FOOD_FRUITS', 'FOOD_VEGETABLES', 'VEHICLES_AIR', 'VEHICLES_WATER')
  AND NOT EXISTS (SELECT 1 FROM topics c WHERE c.parent_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.topic_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM game_sessions s WHERE s.topic_id = t.id);

-- MATH was a sample code of the first dev content and used to be switched off by the clean-up above. It is a root topic
-- of vocabulary v2 (V16) now, so it is no longer in that list and a database where it was switched off gets it back.
UPDATE topics SET is_active = TRUE WHERE code = 'MATH' AND NOT is_active;

-- Questions created by the very first (static options) seed became inactive "LEGACY" rows in V5: drop them when unused.
DELETE FROM game_questions q
WHERE q.correct_answer_item_id IN (SELECT id FROM answer_items WHERE code LIKE 'LEGACY\_%')
  AND NOT EXISTS (SELECT 1 FROM session_questions sq WHERE sq.question_id = q.id);

DELETE FROM answer_items a
WHERE a.code LIKE 'LEGACY\_%'
  AND NOT EXISTS (SELECT 1 FROM game_questions q WHERE q.correct_answer_item_id = a.id);

-- ---------------------------------------------------------------------------
-- Vocabulary v2 (V16) owns the topic tree and the words: the rows above are only the OLD sample set. On a fresh
-- database V16 runs before this file, so the old topics and words that V16 does not know are switched off here.
-- v2 answer items all carry metadata.age_group; the old ones do not.
-- ---------------------------------------------------------------------------
-- The hand-written (CURATED) questions above are not used any more (short template questions only). The upsert above
-- switches them on again, so they are switched off here, not only in R__zz_templates.sql (which runs only when it changes).
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
