BEGIN TRANSACTION READ ONLY;
SET LOCAL search_path = public, pg_catalog;

DO $$
DECLARE
    table_mismatch_count integer;
    column_mismatch_count integer;
    constraint_mismatch_count integer;
    index_mismatch_count integer;
    unexpired_legacy_count bigint;
    orphan_legacy_count bigint;
BEGIN
    WITH expected_tables(table_name) AS (
        VALUES
            ('booking_participants'),
            ('bookings'),
            ('idempotency_keys'),
            ('outbox_events'),
            ('tour_slots')
    ),
    actual_tables AS (
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_type = 'BASE TABLE'
    ),
    differences AS (
        (SELECT * FROM expected_tables EXCEPT SELECT * FROM actual_tables)
        UNION ALL
        (SELECT * FROM actual_tables EXCEPT SELECT * FROM expected_tables)
    )
    SELECT count(*) INTO table_mismatch_count FROM differences;

    WITH expected_columns(
        table_name,
        column_name,
        ordinal_position,
        data_type,
        character_maximum_length,
        numeric_precision,
        numeric_scale,
        is_nullable,
        column_default
    ) AS (
        VALUES
            ('booking_participants', 'id', 1, 'uuid', NULL::integer, NULL::integer, NULL::integer, 'NO', NULL::text),
            ('booking_participants', 'full_name', 2, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('booking_participants', 'is_primary_contact', 3, 'boolean', NULL, NULL, NULL, 'NO', NULL),
            ('booking_participants', 'phone', 4, 'character varying', 255, NULL, NULL, 'YES', NULL),
            ('booking_participants', 'booking_id', 5, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('bookings', 'id', 1, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('bookings', 'created_at', 2, 'timestamp with time zone', NULL, NULL, NULL, 'NO', NULL),
            ('bookings', 'customer_id', 3, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('bookings', 'generated_itinerary_id', 4, 'character varying', 255, NULL, NULL, 'YES', NULL),
            ('bookings', 'hold_expires_at', 5, 'timestamp with time zone', NULL, NULL, NULL, 'NO', NULL),
            ('bookings', 'participant_count', 6, 'integer', NULL, 32, 0, 'NO', NULL),
            ('bookings', 'status', 7, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('bookings', 'total_amount', 8, 'numeric', NULL, 38, 2, 'NO', NULL),
            ('bookings', 'tour_slot_id', 9, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('bookings', 'updated_at', 10, 'timestamp with time zone', NULL, NULL, NULL, 'NO', NULL),
            ('idempotency_keys', 'key', 1, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('idempotency_keys', 'booking_id', 2, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('idempotency_keys', 'created_at', 3, 'timestamp with time zone', NULL, NULL, NULL, 'NO', NULL),
            ('idempotency_keys', 'expires_at', 4, 'timestamp with time zone', NULL, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'id', 1, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'aggregate_id', 2, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'aggregate_type', 3, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'created_at', 4, 'timestamp with time zone', NULL, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'event_type', 5, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'payload', 6, 'text', NULL, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'published', 7, 'boolean', NULL, NULL, NULL, 'NO', NULL),
            ('tour_slots', 'id', 1, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('tour_slots', 'booked_count', 2, 'integer', NULL, 32, 0, 'NO', NULL),
            ('tour_slots', 'departure_date', 3, 'date', NULL, NULL, NULL, 'NO', NULL),
            ('tour_slots', 'max_capacity', 4, 'integer', NULL, 32, 0, 'NO', NULL),
            ('tour_slots', 'status', 5, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('tour_slots', 'tour_id', 6, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('tour_slots', 'version', 7, 'integer', NULL, 32, 0, 'NO', NULL)
    ),
    actual_columns AS (
        SELECT
            table_name,
            column_name,
            ordinal_position,
            data_type,
            character_maximum_length,
            numeric_precision,
            numeric_scale,
            is_nullable,
            column_default
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name IN (
              'booking_participants',
              'bookings',
              'idempotency_keys',
              'outbox_events',
              'tour_slots'
          )
    ),
    differences AS (
        (SELECT * FROM expected_columns EXCEPT SELECT * FROM actual_columns)
        UNION ALL
        (SELECT * FROM actual_columns EXCEPT SELECT * FROM expected_columns)
    )
    SELECT count(*) INTO column_mismatch_count FROM differences;

    WITH actual_relational_constraints AS (
        SELECT
            source_schema.nspname::text AS source_schema,
            source_table.relname::text AS source_table,
            constraint_definition.contype::text AS constraint_type,
            ARRAY(
                SELECT source_attribute.attname::text
                FROM unnest(constraint_definition.conkey) WITH ORDINALITY
                    source_key(attribute_number, position)
                JOIN pg_catalog.pg_attribute source_attribute
                  ON source_attribute.attrelid = source_table.oid
                 AND source_attribute.attnum = source_key.attribute_number
                 AND NOT source_attribute.attisdropped
                ORDER BY source_key.position
            ) AS source_columns,
            CASE
                WHEN constraint_definition.contype = 'f' THEN target_schema.nspname::text
                ELSE NULL::text
            END AS target_schema,
            CASE
                WHEN constraint_definition.contype = 'f' THEN target_table.relname::text
                ELSE NULL::text
            END AS target_table,
            CASE
                WHEN constraint_definition.contype = 'f' THEN ARRAY(
                    SELECT target_attribute.attname::text
                    FROM unnest(constraint_definition.confkey) WITH ORDINALITY
                        target_key(attribute_number, position)
                    JOIN pg_catalog.pg_attribute target_attribute
                      ON target_attribute.attrelid = target_table.oid
                     AND target_attribute.attnum = target_key.attribute_number
                     AND NOT target_attribute.attisdropped
                    ORDER BY target_key.position
                )
                ELSE NULL::text[]
            END AS target_columns,
            CASE
                WHEN constraint_definition.contype = 'f' THEN constraint_definition.confupdtype::text
                ELSE NULL::text
            END AS update_action,
            CASE
                WHEN constraint_definition.contype = 'f' THEN constraint_definition.confdeltype::text
                ELSE NULL::text
            END AS delete_action,
            CASE
                WHEN constraint_definition.contype = 'f' THEN constraint_definition.confmatchtype::text
                ELSE NULL::text
            END AS match_type,
            constraint_definition.condeferrable AS is_deferrable,
            constraint_definition.condeferred AS initially_deferred,
            constraint_definition.convalidated AS is_validated
        FROM pg_catalog.pg_constraint constraint_definition
        JOIN pg_catalog.pg_class source_table
          ON source_table.oid = constraint_definition.conrelid
        JOIN pg_catalog.pg_namespace source_schema
          ON source_schema.oid = source_table.relnamespace
        LEFT JOIN pg_catalog.pg_class target_table
          ON target_table.oid = constraint_definition.confrelid
         AND constraint_definition.contype = 'f'
        LEFT JOIN pg_catalog.pg_namespace target_schema
          ON target_schema.oid = target_table.relnamespace
        WHERE source_schema.nspname = 'public'
          AND source_table.relname IN (
              'booking_participants',
              'bookings',
              'idempotency_keys',
              'outbox_events',
              'tour_slots'
          )
          AND constraint_definition.contype IN ('p', 'u', 'f')
    ),
    expected_relational_constraints(
        source_schema,
        source_table,
        constraint_type,
        source_columns,
        target_schema,
        target_table,
        target_columns,
        update_action,
        delete_action,
        match_type,
        is_deferrable,
        initially_deferred,
        is_validated
    ) AS (
        VALUES
            ('public', 'booking_participants', 'p', ARRAY['id']::text[], NULL::text, NULL::text, NULL::text[], NULL::text, NULL::text, NULL::text, false, false, true),
            ('public', 'booking_participants', 'f', ARRAY['booking_id']::text[], 'public', 'bookings', ARRAY['id']::text[], 'a', 'a', 's', false, false, true),
            ('public', 'bookings', 'p', ARRAY['id']::text[], NULL, NULL, NULL, NULL, NULL, NULL, false, false, true),
            ('public', 'idempotency_keys', 'p', ARRAY['key']::text[], NULL, NULL, NULL, NULL, NULL, NULL, false, false, true),
            ('public', 'outbox_events', 'p', ARRAY['id']::text[], NULL, NULL, NULL, NULL, NULL, NULL, false, false, true),
            ('public', 'tour_slots', 'p', ARRAY['id']::text[], NULL, NULL, NULL, NULL, NULL, NULL, false, false, true),
            ('public', 'tour_slots', 'u', ARRAY['tour_id', 'departure_date']::text[], NULL, NULL, NULL, NULL, NULL, NULL, false, false, true)
    ),
    relational_differences AS (
        (SELECT * FROM expected_relational_constraints EXCEPT SELECT * FROM actual_relational_constraints)
        UNION ALL
        (SELECT * FROM actual_relational_constraints EXCEPT SELECT * FROM expected_relational_constraints)
    ),
    actual_check_constraints AS (
        SELECT
            source_schema.nspname::text AS source_schema,
            source_table.relname::text AS source_table,
            ARRAY(
                SELECT source_attribute.attname::text
                FROM unnest(constraint_definition.conkey) WITH ORDINALITY
                    source_key(attribute_number, position)
                JOIN pg_catalog.pg_attribute source_attribute
                  ON source_attribute.attrelid = source_table.oid
                 AND source_attribute.attnum = source_key.attribute_number
                 AND NOT source_attribute.attisdropped
                ORDER BY source_key.position
            ) AS source_columns,
            ARRAY(
                SELECT DISTINCT (matched_value)[1]
                FROM regexp_matches(
                    pg_get_expr(constraint_definition.conbin, constraint_definition.conrelid, true),
                    '''([^'']+)''',
                    'g'
                ) AS matched_values(matched_value)
                ORDER BY (matched_value)[1]
            ) AS accepted_values,
            lower(
                regexp_replace(
                    regexp_replace(
                        regexp_replace(
                            pg_get_expr(constraint_definition.conbin, constraint_definition.conrelid, true),
                            '::(character varying|text)(\[\])?',
                            '',
                            'g'
                        ),
                        '''[^'']*''',
                        '?',
                        'g'
                    ),
                    '[[:space:]]+',
                    '',
                    'g'
                )
            ) ~ '^status=any\(array\[\?(,\?)*\]\)$' AS has_expected_predicate_shape,
            constraint_definition.convalidated AS is_validated
        FROM pg_catalog.pg_constraint constraint_definition
        JOIN pg_catalog.pg_class source_table
          ON source_table.oid = constraint_definition.conrelid
        JOIN pg_catalog.pg_namespace source_schema
          ON source_schema.oid = source_table.relnamespace
        WHERE source_schema.nspname = 'public'
          AND source_table.relname IN (
              'booking_participants',
              'bookings',
              'idempotency_keys',
              'outbox_events',
              'tour_slots'
          )
          AND constraint_definition.contype = 'c'
    ),
    expected_check_constraints(
        source_schema,
        source_table,
        source_columns,
        accepted_values,
        has_expected_predicate_shape,
        is_validated
    ) AS (
        VALUES
            ('public', 'bookings', ARRAY['status']::text[], ARRAY['CANCELLED', 'COMPLETED', 'CONFIRMED', 'EXPIRED', 'PAYMENT_FAILED', 'PENDING']::text[], true, true),
            ('public', 'tour_slots', ARRAY['status']::text[], ARRAY['CLOSED', 'OPEN']::text[], true, true)
    ),
    check_differences AS (
        (SELECT * FROM expected_check_constraints EXCEPT SELECT * FROM actual_check_constraints)
        UNION ALL
        (SELECT * FROM actual_check_constraints EXCEPT SELECT * FROM expected_check_constraints)
    )
    SELECT
        (SELECT count(*) FROM relational_differences)
        + (SELECT count(*) FROM check_differences)
    INTO constraint_mismatch_count;

    WITH actual_indexes AS (
        SELECT
            table_definition.relname::text AS table_name,
            index_definition.indisprimary,
            index_definition.indisunique,
            ARRAY(
                SELECT attribute.attname::text
                FROM unnest(index_definition.indkey) WITH ORDINALITY key_column(attribute_number, position)
                JOIN pg_catalog.pg_attribute attribute
                  ON attribute.attrelid = table_definition.oid
                 AND attribute.attnum = key_column.attribute_number
                ORDER BY key_column.position
            ) AS columns,
            index_definition.indpred IS NOT NULL AS has_predicate,
            index_definition.indexprs IS NOT NULL AS has_expression
        FROM pg_catalog.pg_index index_definition
        JOIN pg_catalog.pg_class table_definition
          ON table_definition.oid = index_definition.indrelid
        JOIN pg_catalog.pg_namespace schema_definition
          ON schema_definition.oid = table_definition.relnamespace
        WHERE schema_definition.nspname = 'public'
          AND table_definition.relname IN (
              'booking_participants',
              'bookings',
              'idempotency_keys',
              'outbox_events',
              'tour_slots'
          )
    ),
    expected_indexes(table_name, indisprimary, indisunique, columns, has_predicate, has_expression) AS (
        VALUES
            ('booking_participants', true, true, ARRAY['id']::text[], false, false),
            ('bookings', true, true, ARRAY['id']::text[], false, false),
            ('idempotency_keys', true, true, ARRAY['key']::text[], false, false),
            ('outbox_events', true, true, ARRAY['id']::text[], false, false),
            ('tour_slots', true, true, ARRAY['id']::text[], false, false),
            ('tour_slots', false, true, ARRAY['tour_id', 'departure_date']::text[], false, false)
    ),
    differences AS (
        (SELECT * FROM expected_indexes EXCEPT SELECT * FROM actual_indexes)
        UNION ALL
        (SELECT * FROM actual_indexes EXCEPT SELECT * FROM expected_indexes)
    )
    SELECT count(*) INTO index_mismatch_count FROM differences;

    IF table_mismatch_count <> 0
        OR column_mismatch_count <> 0
        OR constraint_mismatch_count <> 0
        OR index_mismatch_count <> 0 THEN
        RAISE EXCEPTION
            'Legacy schema is not compatible with V1 (tables %, columns %, constraints %, indexes %)',
            table_mismatch_count,
            column_mismatch_count,
            constraint_mismatch_count,
            index_mismatch_count;
    END IF;

    SELECT count(*)
    INTO unexpired_legacy_count
    FROM public.idempotency_keys
    WHERE expires_at > CURRENT_TIMESTAMP;

    SELECT count(*)
    INTO orphan_legacy_count
    FROM public.idempotency_keys legacy_key
    LEFT JOIN public.bookings booking ON booking.id = legacy_key.booking_id
    WHERE booking.id IS NULL;

    IF unexpired_legacy_count <> 0 OR orphan_legacy_count <> 0 THEN
        RAISE EXCEPTION
            'Legacy data is not ready for V3 (unexpired %, orphaned %)',
            unexpired_legacy_count,
            orphan_legacy_count;
    END IF;
END
$$;

SELECT
    'LEGACY_V1_COMPATIBLE' AS fingerprint,
    count(*) AS legacy_idempotency_records,
    count(*) FILTER (WHERE expires_at > CURRENT_TIMESTAMP) AS unexpired_legacy_records
FROM public.idempotency_keys;

COMMIT;
