CREATE TABLE public.notification_recipients (
    auth_user_id uuid NOT NULL,
    email character varying(320),
    full_name character varying(255),
    email_notifications_enabled boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT notification_recipients_pkey PRIMARY KEY (auth_user_id)
);

CREATE TABLE public.notification_event_inbox (
    event_id uuid NOT NULL,
    source_topic character varying(64) NOT NULL,
    event_type character varying(96) NOT NULL,
    aggregate_id character varying(255),
    event_payload text NOT NULL,
    status character varying(24) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp with time zone,
    last_error character varying(1000),
    received_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at timestamp with time zone,
    CONSTRAINT notification_event_inbox_pkey PRIMARY KEY (event_id),
    CONSTRAINT notification_event_inbox_status_check CHECK (
        status IN ('RECEIVED', 'WAITING', 'PROCESSED', 'IGNORED', 'FAILED')
    ),
    CONSTRAINT notification_event_inbox_attempt_count_check CHECK (attempt_count >= 0)
);

CREATE INDEX idx_notification_event_inbox_retry
    ON public.notification_event_inbox (next_attempt_at, received_at)
    WHERE status IN ('RECEIVED', 'WAITING');

CREATE TABLE public.booking_notification_recipients (
    booking_id uuid NOT NULL,
    auth_user_id uuid NOT NULL,
    tour_id character varying(255),
    booking_status character varying(48) NOT NULL,
    start_date date,
    end_date date,
    reminder_sent_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT booking_notification_recipients_pkey PRIMARY KEY (booking_id)
);

CREATE INDEX idx_booking_notification_reminders
    ON public.booking_notification_recipients (start_date, booking_id)
    WHERE booking_status = 'CONFIRMED' AND reminder_sent_at IS NULL;

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    auth_user_id uuid NOT NULL,
    event_id uuid NOT NULL,
    notification_type character varying(64) NOT NULL,
    category character varying(32) NOT NULL,
    title character varying(160) NOT NULL,
    message character varying(1000) NOT NULL,
    action_url character varying(500),
    reference_type character varying(32),
    reference_id character varying(255),
    read_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT notifications_event_id_key UNIQUE (event_id),
    CONSTRAINT notifications_category_check CHECK (category IN ('BOOKING', 'PAYMENT', 'DEPARTURE', 'SYSTEM'))
);

CREATE INDEX idx_notifications_user_created
    ON public.notifications (auth_user_id, created_at DESC, id DESC);

CREATE INDEX idx_notifications_user_unread
    ON public.notifications (auth_user_id, created_at DESC)
    WHERE read_at IS NULL;

CREATE TABLE public.notification_email_deliveries (
    id uuid NOT NULL,
    notification_id uuid NOT NULL,
    recipient_email character varying(320),
    subject character varying(200) NOT NULL,
    body text NOT NULL,
    status character varying(32) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp with time zone,
    last_error character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    sent_at timestamp with time zone,
    CONSTRAINT notification_email_deliveries_pkey PRIMARY KEY (id),
    CONSTRAINT notification_email_deliveries_notification_key UNIQUE (notification_id),
    CONSTRAINT notification_email_deliveries_notification_fkey FOREIGN KEY (notification_id)
        REFERENCES public.notifications (id),
    CONSTRAINT notification_email_deliveries_status_check CHECK (
        status IN ('PENDING', 'WAITING_RECIPIENT', 'SENT', 'SKIPPED', 'FAILED')
    ),
    CONSTRAINT notification_email_deliveries_attempt_count_check CHECK (attempt_count >= 0)
);

CREATE INDEX idx_notification_email_deliveries_dispatch
    ON public.notification_email_deliveries (next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'WAITING_RECIPIENT');
