BEGIN TRANSACTION READ ONLY;
SET LOCAL search_path = public, pg_catalog;

DO $$
DECLARE
    table_mismatch_count integer;
    column_mismatch_count integer;
    constraint_mismatch_count integer;
    index_mismatch_count integer;
    duplicate_active_booking_count bigint;
BEGIN
    WITH expected_tables(table_name) AS (
        VALUES
            ('outbox_events'),
            ('payment_logs'),
            ('payments'),
            ('refunds')
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
            ('outbox_events', 'id', 1, 'uuid', NULL::integer, NULL::integer, NULL::integer, 'NO', NULL::text),
            ('outbox_events', 'aggregate_id', 2, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'aggregate_type', 3, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'created_at', 4, 'timestamp with time zone', NULL, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'event_type', 5, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'payload', 6, 'text', NULL, NULL, NULL, 'NO', NULL),
            ('outbox_events', 'published', 7, 'boolean', NULL, NULL, NULL, 'NO', NULL),
            ('payment_logs', 'id', 1, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('payment_logs', 'event_source', 2, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('payment_logs', 'payment_id', 3, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('payment_logs', 'raw_payload', 4, 'text', NULL, NULL, NULL, 'NO', NULL),
            ('payment_logs', 'received_at', 5, 'timestamp with time zone', NULL, NULL, NULL, 'NO', NULL),
            ('payments', 'id', 1, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('payments', 'amount', 2, 'numeric', NULL, 38, 2, 'NO', NULL),
            ('payments', 'booking_id', 3, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('payments', 'completed_at', 4, 'timestamp with time zone', NULL, NULL, NULL, 'YES', NULL),
            ('payments', 'created_at', 5, 'timestamp with time zone', NULL, NULL, NULL, 'NO', NULL),
            ('payments', 'currency', 6, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('payments', 'gateway', 7, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('payments', 'gateway_transaction_ref', 8, 'character varying', 255, NULL, NULL, 'YES', NULL),
            ('payments', 'status', 9, 'character varying', 255, NULL, NULL, 'NO', NULL),
            ('refunds', 'id', 1, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('refunds', 'amount', 2, 'numeric', NULL, 38, 2, 'NO', NULL),
            ('refunds', 'completed_at', 3, 'timestamp with time zone', NULL, NULL, NULL, 'YES', NULL),
            ('refunds', 'created_at', 4, 'timestamp with time zone', NULL, NULL, NULL, 'NO', NULL),
            ('refunds', 'gateway_refund_ref', 5, 'character varying', 255, NULL, NULL, 'YES', NULL),
            ('refunds', 'payment_id', 6, 'uuid', NULL, NULL, NULL, 'NO', NULL),
            ('refunds', 'percentage', 7, 'integer', NULL, 32, 0, 'NO', NULL),
            ('refunds', 'status', 8, 'character varying', 255, NULL, NULL, 'NO', NULL)
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
          AND table_name IN ('outbox_events', 'payment_logs', 'payments', 'refunds')
    ),
    differences AS (
        (SELECT * FROM expected_columns EXCEPT SELECT * FROM actual_columns)
        UNION ALL
        (SELECT * FROM actual_columns EXCEPT SELECT * FROM expected_columns)
    )
    SELECT count(*) INTO column_mismatch_count FROM differences;

    WITH actual_relational_constraints AS (
        SELECT
            source_table.relname::text AS table_name,
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
            ) AS columns,
            constraint_definition.convalidated AS is_validated
        FROM pg_catalog.pg_constraint constraint_definition
        JOIN pg_catalog.pg_class source_table
          ON source_table.oid = constraint_definition.conrelid
        JOIN pg_catalog.pg_namespace source_schema
          ON source_schema.oid = source_table.relnamespace
        WHERE source_schema.nspname = 'public'
          AND source_table.relname IN ('outbox_events', 'payment_logs', 'payments', 'refunds')
          AND constraint_definition.contype IN ('p', 'u', 'f')
    ),
    expected_relational_constraints(table_name, constraint_type, columns, is_validated) AS (
        VALUES
            ('outbox_events', 'p', ARRAY['id']::text[], true),
            ('payment_logs', 'p', ARRAY['id']::text[], true),
            ('payments', 'p', ARRAY['id']::text[], true),
            ('payments', 'u', ARRAY['gateway_transaction_ref']::text[], true),
            ('refunds', 'p', ARRAY['id']::text[], true)
    ),
    relational_differences AS (
        (SELECT * FROM expected_relational_constraints EXCEPT SELECT * FROM actual_relational_constraints)
        UNION ALL
        (SELECT * FROM actual_relational_constraints EXCEPT SELECT * FROM expected_relational_constraints)
    ),
    actual_check_constraints AS (
        SELECT
            source_table.relname::text AS table_name,
            ARRAY(
                SELECT source_attribute.attname::text
                FROM unnest(constraint_definition.conkey) WITH ORDINALITY
                    source_key(attribute_number, position)
                JOIN pg_catalog.pg_attribute source_attribute
                  ON source_attribute.attrelid = source_table.oid
                 AND source_attribute.attnum = source_key.attribute_number
                 AND NOT source_attribute.attisdropped
                ORDER BY source_key.position
            ) AS columns,
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
            ) ~ '^(gateway|status)=any\(array\[\?(,\?)*\]\)$' AS has_expected_predicate_shape,
            constraint_definition.convalidated AS is_validated
        FROM pg_catalog.pg_constraint constraint_definition
        JOIN pg_catalog.pg_class source_table
          ON source_table.oid = constraint_definition.conrelid
        JOIN pg_catalog.pg_namespace source_schema
          ON source_schema.oid = source_table.relnamespace
        WHERE source_schema.nspname = 'public'
          AND source_table.relname IN ('outbox_events', 'payment_logs', 'payments', 'refunds')
          AND constraint_definition.contype = 'c'
    ),
    expected_check_constraints(
        table_name,
        columns,
        accepted_values,
        has_expected_predicate_shape,
        is_validated
    ) AS (
        VALUES
            ('payments', ARRAY['gateway']::text[], ARRAY['STRIPE', 'VNPAY']::text[], true, true),
            ('payments', ARRAY['status']::text[], ARRAY['CANCELLED', 'FAILED', 'INITIATED', 'SUCCESS']::text[], true, true),
            ('refunds', ARRAY['status']::text[], ARRAY['MANUAL_REQUIRED', 'PENDING', 'SUCCESS']::text[], true, true)
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
                FROM unnest(index_definition.indkey) WITH ORDINALITY
                    key_column(attribute_number, position)
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
          AND table_definition.relname IN ('outbox_events', 'payment_logs', 'payments', 'refunds')
    ),
    expected_indexes(table_name, indisprimary, indisunique, columns, has_predicate, has_expression) AS (
        VALUES
            ('outbox_events', true, true, ARRAY['id']::text[], false, false),
            ('payment_logs', true, true, ARRAY['id']::text[], false, false),
            ('payments', true, true, ARRAY['id']::text[], false, false),
            ('payments', false, true, ARRAY['gateway_transaction_ref']::text[], false, false),
            ('refunds', true, true, ARRAY['id']::text[], false, false)
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
            'Legacy Payment schema is not compatible with V1 (tables %, columns %, constraints %, indexes %)',
            table_mismatch_count,
            column_mismatch_count,
            constraint_mismatch_count,
            index_mismatch_count;
    END IF;

    SELECT count(*)
    INTO duplicate_active_booking_count
    FROM (
        SELECT booking_id
        FROM public.payments
        WHERE status IN ('INITIATED', 'SUCCESS')
        GROUP BY booking_id
        HAVING count(*) > 1
    ) duplicate_bookings;

    IF duplicate_active_booking_count <> 0 THEN
        RAISE EXCEPTION
            'Legacy Payment data is not ready for V2: % booking(s) have multiple INITIATED or SUCCESS payments',
            duplicate_active_booking_count;
    END IF;
END
$$;

SELECT
    'PAYMENT_LEGACY_V1_COMPATIBLE' AS fingerprint,
    (SELECT count(*) FROM public.payments) AS payment_records,
    (
        SELECT count(*)
        FROM public.payments
        WHERE status = 'INITIATED'
    ) AS initiated_payment_records;

COMMIT;
