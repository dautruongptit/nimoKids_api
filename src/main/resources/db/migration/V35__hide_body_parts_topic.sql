-- Hides the topic BODY_PARTS ("Bộ phận cơ thể"): it is switched off, not deleted, so its questions and words stay and
-- UPDATE topics SET is_active = TRUE WHERE code = 'BODY_PARTS' brings it back. Idempotent.
UPDATE topics SET is_active = FALSE, updated_at = now() WHERE code = 'BODY_PARTS' AND is_active;
