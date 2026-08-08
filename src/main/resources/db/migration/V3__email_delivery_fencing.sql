alter table email_delivery_jobs add column delivery_fence varchar(64);
update email_delivery_jobs set delivery_fence = md5(random()::text || clock_timestamp()::text) where delivery_fence is null;
alter table email_delivery_jobs alter column delivery_fence set not null;
create index email_delivery_jobs_lease_idx on email_delivery_jobs(state, lease_until);
