DO $$
DECLARE
    unexpired_legacy_count bigint;
    orphan_legacy_count bigint;
BEGIN
    SELECT count(*)
    INTO unexpired_legacy_count
    FROM public.idempotency_keys
    WHERE expires_at > CURRENT_TIMESTAMP;

    IF unexpired_legacy_count > 0 THEN
        RAISE EXCEPTION
            'Cannot migrate idempotency_keys: % legacy record(s) have not expired',
            unexpired_legacy_count;
    END IF;

    SELECT count(*)
    INTO orphan_legacy_count
    FROM public.idempotency_keys legacy_key
    LEFT JOIN public.bookings booking ON booking.id = legacy_key.booking_id
    WHERE booking.id IS NULL;

    IF orphan_legacy_count > 0 THEN
        RAISE EXCEPTION
            'Cannot migrate idempotency_keys: % legacy record(s) reference missing bookings',
            orphan_legacy_count;
    END IF;
END
$$;

ALTER TABLE public.idempotency_keys
    ADD COLUMN customer_id uuid,
    ADD COLUMN record_state character varying(32),
    ADD COLUMN request_hash character varying(64),
    ADD COLUMN hash_version character varying(32),
    ADD COLUMN response_snapshot text;

UPDATE public.idempotency_keys legacy_key
SET customer_id = booking.customer_id,
    record_state = 'LEGACY_EXPIRED'
FROM public.bookings booking
WHERE booking.id = legacy_key.booking_id;

ALTER TABLE public.idempotency_keys
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN record_state SET NOT NULL,
    ALTER COLUMN booking_id DROP NOT NULL;

DO $$
DECLARE
    primary_key_count integer;
    primary_key_name name;
    primary_key_columns smallint[];
    key_attribute_number smallint;
BEGIN
    SELECT attribute.attnum
    INTO key_attribute_number
    FROM pg_catalog.pg_attribute attribute
    WHERE attribute.attrelid = 'public.idempotency_keys'::regclass
      AND attribute.attname = 'key'
      AND NOT attribute.attisdropped;

    SELECT count(*)
    INTO primary_key_count
    FROM pg_catalog.pg_constraint constraint_definition
    WHERE constraint_definition.conrelid = 'public.idempotency_keys'::regclass
      AND constraint_definition.contype = 'p';

    IF primary_key_count <> 1 THEN
        RAISE EXCEPTION
            'Expected idempotency_keys to have exactly one primary key';
    END IF;

    SELECT constraint_definition.conname, constraint_definition.conkey
    INTO primary_key_name, primary_key_columns
    FROM pg_catalog.pg_constraint constraint_definition
    WHERE constraint_definition.conrelid = 'public.idempotency_keys'::regclass
      AND constraint_definition.contype = 'p';

    IF primary_key_columns <> ARRAY[key_attribute_number]::smallint[] THEN
        RAISE EXCEPTION
            'Expected idempotency_keys primary key to contain only column key';
    END IF;

    EXECUTE format(
        'ALTER TABLE public.idempotency_keys DROP CONSTRAINT %I',
        primary_key_name
    );
END
$$;

ALTER TABLE public.idempotency_keys
    ADD CONSTRAINT idempotency_keys_pkey PRIMARY KEY (customer_id, key),
    ADD CONSTRAINT idempotency_keys_record_state_check CHECK (
        record_state IN ('LEGACY_EXPIRED', 'PROCESSING', 'COMPLETED')
    ),
    ADD CONSTRAINT idempotency_keys_state_payload_check CHECK (
        (
            record_state = 'LEGACY_EXPIRED'
            AND booking_id IS NOT NULL
            AND request_hash IS NULL
            AND hash_version IS NULL
            AND response_snapshot IS NULL
        )
        OR
        (
            record_state = 'PROCESSING'
            AND booking_id IS NULL
            AND request_hash ~ '^[0-9a-f]{64}$'
            AND hash_version = 'SHA256_V1'
            AND response_snapshot IS NULL
            AND expires_at > created_at
        )
        OR
        (
            record_state = 'COMPLETED'
            AND booking_id IS NOT NULL
            AND request_hash ~ '^[0-9a-f]{64}$'
            AND hash_version = 'SHA256_V1'
            AND response_snapshot IS NOT NULL
            AND expires_at > created_at
        )
    );
