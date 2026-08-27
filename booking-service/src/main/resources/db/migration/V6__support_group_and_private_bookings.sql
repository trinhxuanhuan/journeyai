ALTER TABLE public.bookings
    ADD COLUMN tour_id character varying(255),
    ADD COLUMN booking_type character varying(16) NOT NULL DEFAULT 'GROUP',
    ADD COLUMN start_date date,
    ADD COLUMN end_date date,
    ADD COLUMN price_model character varying(16) NOT NULL DEFAULT 'PER_PERSON',
    ADD COLUMN unit_price numeric(38, 2),
    ADD COLUMN commercial_snapshot text,
    ADD COLUMN cancellation_policy_snapshot text,
    ADD COLUMN assigned_guide_id character varying(255),
    ADD COLUMN guide_option_selected boolean NOT NULL DEFAULT false,
    ADD COLUMN single_room_count integer NOT NULL DEFAULT 0;

UPDATE public.bookings booking
SET tour_id = departure.tour_id,
    start_date = departure.departure_date,
    end_date = COALESCE(departure.end_date, departure.departure_date),
    unit_price = CASE
        WHEN booking.participant_count > 0
        THEN booking.total_amount / booking.participant_count
        ELSE booking.total_amount
    END,
    commercial_snapshot = json_build_object(
        'source', 'LEGACY_MIGRATION',
        'tourId', departure.tour_id,
        'departureId', booking.tour_slot_id,
        'priceModel', 'PER_PERSON',
        'totalAmount', booking.total_amount
    )::text,
    cancellation_policy_snapshot = '[{"minimumDaysBeforeDeparture":7,"refundPercentage":100},{"minimumDaysBeforeDeparture":3,"refundPercentage":50},{"minimumDaysBeforeDeparture":0,"refundPercentage":0}]'
FROM public.tour_slots departure
WHERE departure.id = booking.tour_slot_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.bookings
        WHERE tour_id IS NULL
           OR start_date IS NULL
           OR end_date IS NULL
           OR unit_price IS NULL
           OR commercial_snapshot IS NULL
           OR cancellation_policy_snapshot IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot backfill bookings: orphan tour_slot_id detected';
    END IF;
END
$$;

ALTER TABLE public.bookings
    ALTER COLUMN tour_id SET NOT NULL,
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN end_date SET NOT NULL,
    ALTER COLUMN unit_price SET NOT NULL,
    ALTER COLUMN commercial_snapshot SET NOT NULL,
    ALTER COLUMN cancellation_policy_snapshot SET NOT NULL,
    ALTER COLUMN tour_slot_id DROP NOT NULL,
    ADD CONSTRAINT bookings_booking_type_check CHECK (booking_type IN ('GROUP', 'PRIVATE')),
    ADD CONSTRAINT bookings_price_model_check CHECK (price_model IN ('PER_PERSON', 'PER_GROUP')),
    ADD CONSTRAINT bookings_dates_check CHECK (end_date >= start_date),
    ADD CONSTRAINT bookings_single_room_count_check CHECK (single_room_count >= 0),
    ADD CONSTRAINT bookings_departure_shape_check CHECK (
        (booking_type = 'GROUP' AND tour_slot_id IS NOT NULL)
        OR (booking_type = 'PRIVATE' AND tour_slot_id IS NULL)
    ),
    ADD CONSTRAINT bookings_tour_slot_fk FOREIGN KEY (tour_slot_id)
        REFERENCES public.tour_slots (id);

ALTER TABLE public.booking_participants
    ADD COLUMN participant_type character varying(16) NOT NULL DEFAULT 'ADULT',
    ADD CONSTRAINT booking_participants_type_check CHECK (participant_type IN ('ADULT', 'CHILD'));

ALTER TABLE public.idempotency_keys
    DROP CONSTRAINT idempotency_keys_state_payload_check;

ALTER TABLE public.idempotency_keys
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
            AND hash_version IN ('SHA256_V1', 'SHA256_V2')
            AND response_snapshot IS NULL
            AND expires_at > created_at
        )
        OR
        (
            record_state = 'COMPLETED'
            AND booking_id IS NOT NULL
            AND request_hash ~ '^[0-9a-f]{64}$'
            AND hash_version IN ('SHA256_V1', 'SHA256_V2')
            AND response_snapshot IS NOT NULL
            AND expires_at > created_at
        )
    );

CREATE INDEX idx_bookings_tour_id_start_date
    ON public.bookings (tour_id, start_date);

CREATE INDEX idx_bookings_tour_slot_id
    ON public.bookings (tour_slot_id)
    WHERE tour_slot_id IS NOT NULL;
