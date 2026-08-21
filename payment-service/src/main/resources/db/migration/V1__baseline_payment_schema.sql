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

CREATE TABLE public.payment_logs (
    id uuid NOT NULL,
    event_source character varying(255) NOT NULL,
    payment_id uuid NOT NULL,
    raw_payload text NOT NULL,
    received_at timestamp with time zone NOT NULL,
    CONSTRAINT payment_logs_pkey PRIMARY KEY (id)
);

CREATE TABLE public.payments (
    id uuid NOT NULL,
    amount numeric(38, 2) NOT NULL,
    booking_id uuid NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    currency character varying(255) NOT NULL,
    gateway character varying(255) NOT NULL,
    gateway_transaction_ref character varying(255),
    status character varying(255) NOT NULL,
    CONSTRAINT payments_pkey PRIMARY KEY (id),
    CONSTRAINT payments_gateway_check CHECK (
        gateway::text = ANY (
            ARRAY[
                'VNPAY'::character varying,
                'STRIPE'::character varying
            ]::text[]
        )
    ),
    CONSTRAINT payments_status_check CHECK (
        status::text = ANY (
            ARRAY[
                'INITIATED'::character varying,
                'SUCCESS'::character varying,
                'FAILED'::character varying,
                'CANCELLED'::character varying
            ]::text[]
        )
    ),
    CONSTRAINT payments_gateway_transaction_ref_key UNIQUE (gateway_transaction_ref)
);

CREATE TABLE public.refunds (
    id uuid NOT NULL,
    amount numeric(38, 2) NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    gateway_refund_ref character varying(255),
    payment_id uuid NOT NULL,
    percentage integer NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT refunds_pkey PRIMARY KEY (id),
    CONSTRAINT refunds_status_check CHECK (
        status::text = ANY (
            ARRAY[
                'PENDING'::character varying,
                'SUCCESS'::character varying,
                'MANUAL_REQUIRED'::character varying
            ]::text[]
        )
    )
);
