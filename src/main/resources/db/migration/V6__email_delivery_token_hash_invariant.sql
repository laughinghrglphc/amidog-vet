-- A pre-V6 row with an invalid/nonexistent linkage cannot be retried safely.
-- Terminalize it without issuing a new token. Completed invalid rows retain no
-- misleading linkage value.
update email_delivery_jobs
set state = 'COMPLETED',
    token_hash = null,
    completed_at = coalesce(completed_at, now()),
    lease_until = null,
    updated_at = now()
where token_hash is null
   or token_hash !~ '^[0-9a-f]{64}$';

alter table email_delivery_jobs
    add constraint email_delivery_jobs_token_hash_ck
    check (
        (token_hash is not null and token_hash ~ '^[0-9a-f]{64}$')
        or (state = 'COMPLETED' and token_hash is null)
    );
