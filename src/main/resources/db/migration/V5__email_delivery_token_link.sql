alter table email_delivery_jobs
    add column token_hash char(64);

-- Jobs created before this migration cannot be tied safely to the link that
-- originally caused them. Close them instead of minting unsolicited links.
update email_delivery_jobs
set state = 'COMPLETED',
    completed_at = coalesce(completed_at, now()),
    lease_until = null,
    updated_at = now()
where token_hash is null
  and state <> 'COMPLETED';

create unique index email_delivery_jobs_token_hash_uq
    on email_delivery_jobs(token_hash)
    where token_hash is not null;
