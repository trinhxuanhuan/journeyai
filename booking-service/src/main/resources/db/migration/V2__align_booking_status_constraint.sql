DO $$
DECLARE
    status_attribute_number smallint;
    status_check_count integer;
    status_check_name name;
BEGIN
    SELECT attribute.attnum
    INTO status_attribute_number
    FROM pg_catalog.pg_attribute attribute
    WHERE attribute.attrelid = 'public.bookings'::regclass
      AND attribute.attname = 'status'
      AND NOT attribute.attisdropped;

    IF status_attribute_number IS NULL THEN
        RAISE EXCEPTION 'Cannot align bookings.status: column public.bookings.status does not exist';
    END IF;

    SELECT count(*)
    INTO status_check_count
    FROM pg_catalog.pg_constraint constraint_definition
    WHERE constraint_definition.conrelid = 'public.bookings'::regclass
      AND constraint_definition.contype = 'c'
      AND status_attribute_number = ANY (constraint_definition.conkey);

    IF status_check_count <> 1 THEN
        RAISE EXCEPTION
            'Expected exactly one CHECK constraint for bookings.status, found %',
            status_check_count;
    END IF;

    SELECT constraint_definition.conname
    INTO status_check_name
    FROM pg_catalog.pg_constraint constraint_definition
    WHERE constraint_definition.conrelid = 'public.bookings'::regclass
      AND constraint_definition.contype = 'c'
      AND status_attribute_number = ANY (constraint_definition.conkey);

    EXECUTE format(
        'ALTER TABLE public.bookings DROP CONSTRAINT %I',
        status_check_name
    );
END
$$;

ALTER TABLE public.bookings
    ADD CONSTRAINT bookings_status_check CHECK (
        status::text = ANY (
            ARRAY[
                'PENDING'::character varying,
                'CONFIRMED'::character varying,
                'EXPIRED'::character varying,
                'PAYMENT_FAILED'::character varying,
                'CANCELLED'::character varying,
                'COMPLETED'::character varying,
                'PAYMENT_REVIEW_REQUIRED'::character varying
            ]::text[]
        )
    );
