ALTER TABLE public.tour_slots
    ADD COLUMN end_date date,
    ADD COLUMN guide_id character varying(255),
    ADD COLUMN price_override numeric(38, 2);

UPDATE public.tour_slots
SET end_date = departure_date
WHERE end_date IS NULL;

-- Legacy slots did not have a concrete guide assignment. Keep them for history,
-- but do not leave them sellable until an administrator assigns a guide.
UPDATE public.tour_slots
SET status = 'CLOSED'
WHERE status = 'OPEN'
  AND guide_id IS NULL;

ALTER TABLE public.tour_slots
    ADD CONSTRAINT tour_slots_capacity_check CHECK (
        max_capacity >= 1
        AND booked_count >= 0
        AND booked_count <= max_capacity
    ),
    ADD CONSTRAINT tour_slots_date_check CHECK (
        end_date IS NULL OR end_date >= departure_date
    ),
    ADD CONSTRAINT tour_slots_price_override_check CHECK (
        price_override IS NULL OR price_override > 0
    );

DO $$
DECLARE
    status_check_name name;
BEGIN
    SELECT constraint_definition.conname
    INTO status_check_name
    FROM pg_catalog.pg_constraint constraint_definition
    JOIN pg_catalog.pg_attribute attribute
      ON attribute.attrelid = constraint_definition.conrelid
     AND attribute.attnum = ANY (constraint_definition.conkey)
    WHERE constraint_definition.conrelid = 'public.tour_slots'::regclass
      AND constraint_definition.contype = 'c'
      AND attribute.attname = 'status'
    LIMIT 1;

    IF status_check_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.tour_slots DROP CONSTRAINT %I', status_check_name);
    END IF;
END
$$;

ALTER TABLE public.tour_slots
    ADD CONSTRAINT tour_slots_status_check CHECK (
        status IN ('OPEN', 'CLOSED', 'CANCELLED', 'COMPLETED')
    );
