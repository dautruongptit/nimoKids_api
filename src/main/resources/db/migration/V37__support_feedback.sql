-- Help screen: feedback that parents send to the product team (bug, idea, request for a question, other).
-- Anyone can send it (no login); the anonymous player is kept only to limit spam and to follow up, and the optional
-- contact is whatever the parent typed. Admins read it through /api/v1/admin/support-feedback.
CREATE TABLE support_feedback (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id  UUID          REFERENCES anonymous_players (id) ON DELETE SET NULL,
    category   VARCHAR(20)   NOT NULL,
    message    VARCHAR(2000) NOT NULL,
    contact    VARCHAR(200),
    language   VARCHAR(10),
    status     VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    admin_note VARCHAR(1000),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_support_feedback_category CHECK (category IN ('BUG', 'IDEA', 'QUESTION_REQUEST', 'OTHER')),
    CONSTRAINT ck_support_feedback_status CHECK (status IN ('NEW', 'SEEN', 'DONE', 'REJECTED'))
);

CREATE INDEX idx_support_feedback_status_created ON support_feedback (status, created_at DESC);
CREATE INDEX idx_support_feedback_player_id ON support_feedback (player_id);
