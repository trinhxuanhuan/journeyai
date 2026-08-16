CREATE TABLE public.bookings (
    id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    customer_id uuid NOT NULL,
    generated_itinerary_id character varying(255),
    hold_expires_at timestamp with time zone NOT NULL,
    participant_count integer NOT NULL,
    status character varying(255) NOT NULL,
    total_amount numeric(38, 2) NOT NULL,
    tour_slot_id uuid NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT bookings_pkey PRIMARY KEY (id),
    CONSTRAINT bookings_status_check CHECK (
        status::text = ANY (
            ARRAY[
                'PENDING'::character varying,
                'CONFIRMED'::character varying,
                'EXPIRED'::character varying,
                'PAYMENT_FAILED'::character varying,
                'CANCELLED'::character varying,
                'COMPLETED'::character varying
            ]::text[]
        )
    )
);

CREATE TABLE public.booking_participants (
    id uuid NOT NULL,
    full_name character varying(255) NOT NULL,
    is_primary_contact boolean NOT NULL,
    phone character varying(255),
    booking_id uuid NOT NULL,
    CONSTRAINT booking_participants_pkey PRIMARY KEY (id),
    CONSTRAINT fkiovubypp1xk3nc2q23a28djch FOREIGN KEY (booking_id)
        REFERENCES public.bookings (id)
);

CREATE TABLE public.idempotency_keys (
    key character varying(255) NOT NULL,
    booking_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT idempotency_keys_pkey PRIMARY KEY (key)
);

CREATE TABLE public.outbox_events (
    id uuid NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_type character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    event_type character varying(255) NOT NULL,
    payload text NOT NULL,
    published boolean NOT NULL,
    CONSTRAINT outbox_events_pkey PRIMARY KEY (id)
);

CREATE TABLE public.tour_slots (
    id uuid NOT NULL,
    booked_count integer NOT NULL,
    departure_date date NOT NULL,
    max_capacity integer NOT NULL,
    status character varying(255) NOT NULL,
    tour_id character varying(255) NOT NULL,
    version integer NOT NULL,
    CONSTRAINT tour_slots_pkey PRIMARY KEY (id),
    CONSTRAINT tour_slots_status_check CHECK (
        status::text = ANY (
            ARRAY[
                'OPEN'::character varying,
                'CLOSED'::character varying
            ]::text[]
        )
    ),
    CONSTRAINT uk9l0k2r9920aivnf93fi0sy42e UNIQUE (tour_id, departure_date)
);
