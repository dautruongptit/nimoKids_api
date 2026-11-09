-- Replaces the pictures of three COLORS words with flat colours (the question picture now fills the whole frame, so any
-- texture shows): black #1A1A1A (was a dotted pattern), gray #9E9E9E (was too light and bluish), pink #FE8AB6 (had faint creases).
-- Merging with || keeps every other tag of the item. Idempotent. Pictures: 512x512 webp.

UPDATE answer_items
SET metadata = metadata || jsonb_build_object('image', 'data:image/webp;base64,UklGRi4AAABXRUJQVlA4TCIAAAAv/8F/AAdQjWpUo/8BgUCyv/cMRfQ/4z//+c9//vOf//wf'), updated_at = now()
WHERE code = 'COLORS_BLACK';

UPDATE answer_items
SET metadata = metadata || jsonb_build_object('image', 'data:image/webp;base64,UklGRi4AAABXRUJQVlA4TCIAAAAv/8F/AAdQz3rWs/8BgUCyv/cMRfQ/4z//+c9//vOf//wf'), updated_at = now()
WHERE code = 'COLORS_GRAY';

UPDATE answer_items
SET metadata = metadata || jsonb_build_object('image', 'data:image/webp;base64,UklGRi4AAABXRUJQVlA4TCIAAAAv/8F/AAdQxfrXtv8BgUCyv/cMRfQ/4z//+c9//vOf//wf'), updated_at = now()
WHERE code = 'COLORS_PINK';
