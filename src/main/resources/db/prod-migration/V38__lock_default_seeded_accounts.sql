-- V3 creates local/demo accounts with the known admin123 password. In production,
-- lock only those exact seeded rows whose password has never been changed.
UPDATE "User"
SET status = 'LOCKED', updated_at = CURRENT_TIMESTAMP
WHERE user_id BETWEEN 'a0000000-0000-0000-0000-000000000001'::uuid
                  AND 'a0000000-0000-0000-0000-000000000013'::uuid
  AND password_hash = '$2a$10$kFRbMu8VP8UH76WNaVtrXefN8TldcFz8bi2TvDZVDd9a0J9xA85ku';
