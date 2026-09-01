BEGIN TRANSACTION READ ONLY;
SET LOCAL search_path = public, pg_catalog;

DO $$
DECLARE
    table_mismatch_count INTEGER;
    column_mismatch_count INTEGER;
    relation_mismatch_count INTEGER;
    invalid_profile_count BIGINT;
    invalid_preference_count BIGINT;
    duplicate_preference_count BIGINT;
BEGIN
    WITH expected_tables(table_name) AS (
        VALUES ('user_preference_tags'), ('user_profiles')
    ),
    actual_tables AS (
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
    ),
    differences AS (
        (SELECT * FROM expected_tables EXCEPT SELECT * FROM actual_tables)
        UNION ALL
        (SELECT * FROM actual_tables EXCEPT SELECT * FROM expected_tables)
    )
    SELECT count(*) INTO table_mismatch_count FROM differences;

    WITH expected_columns(table_name, column_name, data_type, is_nullable) AS (
        VALUES
            ('user_profiles', 'id', 'uuid', 'NO'),
            ('user_profiles', 'auth_user_id', 'uuid', 'NO'),
            ('user_profiles', 'avatar_url', 'character varying', 'YES'),
            ('user_profiles', 'created_at', 'timestamp with time zone', 'NO'),
            ('user_profiles', 'phone', 'character varying', 'YES'),
            ('user_profiles', 'updated_at', 'timestamp with time zone', 'NO'),
            ('user_preference_tags', 'id', 'uuid', 'NO'),
            ('user_preference_tags', 'tag_code', 'character varying', 'NO'),
            ('user_preference_tags', 'updated_at', 'timestamp with time zone', 'NO'),
            ('user_preference_tags', 'weight', 'numeric', 'NO'),
            ('user_preference_tags', 'user_profile_id', 'uuid', 'NO')
    ),
    actual_columns AS (
        SELECT table_name, column_name, data_type, is_nullable
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name IN ('user_profiles', 'user_preference_tags')
    ),
    differences AS (
        (SELECT * FROM expected_columns EXCEPT SELECT * FROM actual_columns)
        UNION ALL
        (SELECT * FROM actual_columns EXCEPT SELECT * FROM expected_columns)
    )
    SELECT count(*) INTO column_mismatch_count FROM differences;

    SELECT
        (CASE WHEN EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.user_profiles'::regclass
              AND contype = 'p'
        ) THEN 0 ELSE 1 END)
        + (CASE WHEN EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.user_profiles'::regclass
              AND contype = 'u'
              AND conkey = ARRAY[
                  (SELECT attnum FROM pg_attribute
                   WHERE attrelid = 'public.user_profiles'::regclass AND attname = 'auth_user_id')
              ]::smallint[]
        ) THEN 0 ELSE 1 END)
        + (CASE WHEN EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.user_preference_tags'::regclass
              AND contype = 'p'
        ) THEN 0 ELSE 1 END)
        + (CASE WHEN EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.user_preference_tags'::regclass
              AND contype = 'f'
              AND confrelid = 'public.user_profiles'::regclass
        ) THEN 0 ELSE 1 END)
    INTO relation_mismatch_count;

    SELECT count(*) INTO invalid_profile_count
    FROM user_profiles
    WHERE (phone IS NOT NULL AND phone <> '' AND phone !~ '^0[0-9]{9}$')
       OR (avatar_url IS NOT NULL AND avatar_url <> ''
           AND (length(avatar_url) > 2048 OR avatar_url !~ '^https://[^[:space:]]+$'));

    SELECT count(*) INTO invalid_preference_count
    FROM user_preference_tags
    WHERE tag_code !~ '^[A-Za-z][A-Za-z0-9_]{1,49}$'
       OR weight < 0
       OR weight > 1;

    SELECT count(*) INTO duplicate_preference_count
    FROM (
        SELECT user_profile_id, upper(tag_code)
        FROM user_preference_tags
        GROUP BY user_profile_id, upper(tag_code)
        HAVING count(*) > 1
    ) duplicate_tags;

    IF table_mismatch_count <> 0
        OR column_mismatch_count <> 0
        OR relation_mismatch_count <> 0
        OR invalid_profile_count <> 0
        OR invalid_preference_count <> 0
        OR duplicate_preference_count <> 0 THEN
        RAISE EXCEPTION
            'Legacy User schema is not compatible (tables %, columns %, relations %, profiles %, preferences %, duplicates %)',
            table_mismatch_count,
            column_mismatch_count,
            relation_mismatch_count,
            invalid_profile_count,
            invalid_preference_count,
            duplicate_preference_count;
    END IF;
END
$$;

SELECT
    'USER_PROFILE_LEGACY_V1_COMPATIBLE' AS fingerprint,
    (SELECT count(*) FROM user_profiles) AS profile_records,
    (SELECT count(*) FROM user_preference_tags) AS preference_records;

COMMIT;
