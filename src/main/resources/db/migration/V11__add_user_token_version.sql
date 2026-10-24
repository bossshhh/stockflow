-- Revoking access tokens before they expire. Every access token carries the user's token_version; bumping
-- it (disable, "sign out everywhere", later: password change) makes every token issued before invalid on
-- the next request. A counter rather than a "valid after" timestamp: JWT issue times have whole-second
-- precision, so a timestamp cannot tell apart tokens issued just before and just after a revocation.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
