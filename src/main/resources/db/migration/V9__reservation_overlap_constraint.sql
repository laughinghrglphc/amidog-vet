alter table reservations
    add constraint reservations_no_occupied_overlap
    exclude using gist (
        tstzrange(scheduled_start, scheduled_end, '[)') with &&
    )
    where (status in ('PENDING', 'CONFIRMED'));
