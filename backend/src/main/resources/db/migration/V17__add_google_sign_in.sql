-- Google sign-in: an account created via Google has no password set. Postgres allows
-- multiple NULLs under a UNIQUE constraint, so this is safe for all existing local-only rows.
ALTER TABLE app_users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE app_users ADD COLUMN google_id VARCHAR(255) UNIQUE;
