-- The 21 number items of the quick arithmetic questions (V26) carried no metadata.age_group. The development seed switches
-- off every item without that tag (R__dev_seed_data.sql: "old vocabulary"), so on a development database they were
-- inactive and CALCULATE for 1-3 years had no usable question ("no active number item with value N").
-- v2 items all carry age_group: give these one and switch them on. Idempotent.
UPDATE answer_items
SET metadata = metadata || '{"age_group": "AGE_1_3"}'::jsonb,
    is_active = TRUE,
    updated_at = now()
WHERE metadata ->> 'category' = 'arithmetic_number'
  AND (NOT (metadata ? 'age_group') OR NOT is_active);
