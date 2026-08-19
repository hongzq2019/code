ALTER TABLE dlt_draw
    ADD COLUMN IF NOT EXISTS vector_error TEXT,
    ADD COLUMN IF NOT EXISTS vector_attempted_at TIMESTAMP WITH TIME ZONE;

UPDATE dlt_draw
SET vector_status = 'PENDING'
WHERE vector_status = 'READY'
  AND vector_attempted_at IS NULL;
