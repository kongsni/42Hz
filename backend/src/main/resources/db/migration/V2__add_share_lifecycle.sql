-- Stage 1 only. V1 is immutable; physical retention and FK changes belong to V3+.
-- No deletion, baseline, or automatic repair of uncertain timestamps.
ALTER TABLE rooms
    ADD COLUMN sharing_blocked boolean NOT NULL DEFAULT false,
    ADD COLUMN retention_review_required boolean NOT NULL DEFAULT false,
    ADD COLUMN retention_review_detected_at timestamp(6) with time zone;

ALTER TABLE shared_results
    ADD COLUMN expires_at timestamp(6) with time zone,
    ADD COLUMN retention_review_required boolean NOT NULL DEFAULT false,
    ADD COLUMN retention_review_detected_at timestamp(6) with time zone;

-- Capture time AFTER both ALTER TABLE locks have been acquired, rather than the
-- transaction start, so legitimate rows committed while waiting are not future rows.
DO $$
DECLARE checked_at timestamptz := clock_timestamp();
BEGIN
    UPDATE rooms
    SET retention_review_required = true, retention_review_detected_at = checked_at
    WHERE NOT isfinite(created_at) OR NOT isfinite(expires_at)
       OR created_at > checked_at
       OR CASE WHEN isfinite(created_at) AND created_at <= checked_at
               THEN expires_at <> created_at + interval '24 hours'
               ELSE true END;

    UPDATE shared_results
    SET expires_at = CASE WHEN isfinite(created_at) AND created_at <= checked_at
                          THEN created_at + interval '168 hours' ELSE NULL END,
        retention_review_required = NOT isfinite(created_at) OR created_at > checked_at,
        retention_review_detected_at = CASE WHEN NOT isfinite(created_at) OR created_at > checked_at
                                            THEN checked_at ELSE NULL END;
END $$;

ALTER TABLE shared_results ADD CONSTRAINT ck_shared_result_expiry_present
    CHECK (retention_review_required OR expires_at IS NOT NULL);
