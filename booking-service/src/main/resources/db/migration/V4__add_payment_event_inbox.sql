CREATE TABLE public.processed_payment_events (
    event_id uuid NOT NULL,
    payment_id uuid NOT NULL,
    booking_id uuid NOT NULL,
    event_type character varying(64) NOT NULL,
    processed_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT processed_payment_events_pkey PRIMARY KEY (event_id),
    CONSTRAINT processed_payment_events_payment_key UNIQUE (payment_id),
    CONSTRAINT processed_payment_events_booking_fkey FOREIGN KEY (booking_id)
        REFERENCES public.bookings (id),
    CONSTRAINT processed_payment_events_type_check CHECK (
        event_type IN ('payment.succeeded', 'payment.failed')
    )
);

CREATE INDEX idx_processed_payment_events_booking_id
    ON public.processed_payment_events (booking_id);
