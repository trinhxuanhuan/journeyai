CREATE TABLE public.processed_booking_events (
    event_id uuid NOT NULL,
    booking_id uuid NOT NULL,
    event_type character varying(64) NOT NULL,
    processed_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT processed_booking_events_pkey PRIMARY KEY (event_id),
    CONSTRAINT processed_booking_events_booking_type_key UNIQUE (booking_id, event_type),
    CONSTRAINT processed_booking_events_type_check CHECK (
        event_type IN ('booking.cancelled', 'booking.late_payment_refund_required')
    )
);

DO $$
BEGIN
    IF EXISTS (
        SELECT payment_id
        FROM public.refunds
        GROUP BY payment_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enable refund idempotency: duplicate refunds exist for the same payment';
    END IF;
END
$$;

CREATE UNIQUE INDEX refunds_one_per_payment_idx
    ON public.refunds (payment_id);
