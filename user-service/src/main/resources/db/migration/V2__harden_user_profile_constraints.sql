DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM user_profiles
        WHERE phone IS NOT NULL
          AND phone <> ''
          AND phone !~ '^0[0-9]{9}$'
    ) THEN
        RAISE EXCEPTION 'Cannot harden user_profiles: invalid phone values exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_profiles
        WHERE avatar_url IS NOT NULL
          AND avatar_url <> ''
          AND (length(avatar_url) > 2048 OR avatar_url !~ '^https://[^[:space:]]+$')
    ) THEN
        RAISE EXCEPTION 'Cannot harden user_profiles: invalid avatar URLs exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_preference_tags
        WHERE tag_code !~ '^[A-Za-z][A-Za-z0-9_]{1,49}$'
           OR weight < 0
           OR weight > 1
    ) THEN
        RAISE EXCEPTION 'Cannot harden user preferences: invalid tag or weight values exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_preference_tags
        GROUP BY user_profile_id, upper(tag_code)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot harden user preferences: duplicate tag codes exist';
    END IF;
END
$$;

UPDATE user_profiles SET phone = NULL WHERE phone = '';
UPDATE user_profiles SET avatar_url = NULL WHERE avatar_url = '';
UPDATE user_preference_tags SET tag_code = upper(tag_code);

ALTER TABLE user_profiles
    ALTER COLUMN phone TYPE VARCHAR(10),
    ALTER COLUMN avatar_url TYPE VARCHAR(2048),
    ADD CONSTRAINT chk_user_profiles_phone
        CHECK (phone IS NULL OR phone ~ '^0[0-9]{9}$'),
    ADD CONSTRAINT chk_user_profiles_avatar_url
        CHECK (avatar_url IS NULL OR avatar_url ~ '^https://[^[:space:]]+$');

ALTER TABLE user_preference_tags
    ALTER COLUMN tag_code TYPE VARCHAR(50),
    ALTER COLUMN weight TYPE NUMERIC(4, 3),
    ADD CONSTRAINT chk_user_preference_tags_weight CHECK (weight >= 0 AND weight <= 1),
    ADD CONSTRAINT uk_user_preference_tags_profile_code UNIQUE (user_profile_id, tag_code);
