DO $$
DECLARE
    duplicate_booking_count bigint;
BEGIN
    SELECT count(*)
    INTO duplicate_booking_count
    FROM (
        SELECT booking_id
        FROM public.payments
        WHERE status IN ('INITIATED', 'SUCCESS')
        GROUP BY booking_id
        HAVING count(*) > 1
    ) duplicate_bookings;

    IF duplicate_booking_count > 0 THEN
        RAISE EXCEPTION
            'Cannot enforce one payable payment per booking: % booking(s) have multiple INITIATED or SUCCESS payments',
            duplicate_booking_count;
    END IF;
END
$$;

CREATE UNIQUE INDEX payments_one_payable_per_booking_idx
    ON public.payments (booking_id)
    WHERE status IN ('INITIATED', 'SUCCESS');

CREATE TABLE public.payment_idempotency_keys (
    customer_id uuid NOT NULL,
    key character varying(255) NOT NULL,
    booking_id uuid,
    payment_id uuid,
    record_state character varying(32) NOT NULL,
    request_hash character varying(64) NOT NULL,
    hash_version character varying(32) NOT NULL,
    response_snapshot text,
    created_at timestamp with time zone NOT NULL,
    replay_expires_at timestamp with time zone NOT NULL,
    key_expires_at timestamp with time zone NOT NULL,
    CONSTRAINT payment_idempotency_keys_pkey PRIMARY KEY (customer_id, key),
    CONSTRAINT payment_idempotency_keys_payment_key UNIQUE (payment_id),
    CONSTRAINT payment_idempotency_keys_payment_fk FOREIGN KEY (payment_id)
        REFERENCES public.payments (id),
    CONSTRAINT payment_idempotency_keys_record_state_check CHECK (
        record_state IN ('PROCESSING', 'COMPLETED')
    ),
    CONSTRAINT payment_idempotency_keys_payload_check CHECK (
        request_hash ~ '^[0-9a-f]{64}$'
        AND hash_version = 'SHA256_V1'
        AND replay_expires_at > created_at
        AND key_expires_at >= replay_expires_at
        AND (
            (
                record_state = 'PROCESSING'
                AND booking_id IS NULL
                AND payment_id IS NULL
                AND response_snapshot IS NULL
            )
            OR
            (
                record_state = 'COMPLETED'
                AND booking_id IS NOT NULL
                AND payment_id IS NOT NULL
                AND response_snapshot IS NOT NULL
            )
        )
    )
);

CREATE INDEX payment_idempotency_keys_key_expires_at_idx
    ON public.payment_idempotency_keys (key_expires_at);
