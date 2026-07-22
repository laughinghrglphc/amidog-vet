do $migration$
declare
    legacy_client record;
    legacy_pet record;
    legacy_service record;
    legacy_reservation record;
    legacy_link record;
    imported_user_id bigint;
    imported_client_id bigint;
    imported_pet_id bigint;
    imported_service_id bigint;
    imported_reservation_id bigint;
    mapped_client_id bigint;
    mapped_pet_id bigint;
    mapped_reservation_id bigint;
    next_client_id bigint;
    next_pet_id bigint;
    next_reservation_id bigint;
    legacy_max_id bigint;
    legacy_service_name varchar(100);
    normalized_email text;
    service_code varchar(60);
    base_service_code varchar(60);
    desired_status varchar(20);
    imported_status varchar(20);
    legacy_start timestamptz;
    collides boolean;
    collision_suffix integer;
begin
    -- A fresh foundation schema has no Phase 1 tables. All legacy references
    -- below are dynamic so PostgreSQL never parses a reference to an absent
    -- relation.
    if to_regclass('legacy_clients') is null then
        return;
    end if;

    -- Explicit legacy identifiers and manually seeded foundation rows can
    -- leave identity sequences behind the current maximum. Move them forward
    -- before any generated identifiers are requested.
    perform setval(
        pg_get_serial_sequence('users', 'id'),
        coalesce((select max(id) from users), 1),
        exists(select 1 from users)
    );
    perform setval(
        pg_get_serial_sequence('services', 'id'),
        coalesce((select max(id) from services), 1),
        exists(select 1 from services)
    );
    perform setval(
        pg_get_serial_sequence('reservation_items', 'id'),
        coalesce((select max(id) from reservation_items), 1),
        exists(select 1 from reservation_items)
    );

    create temporary table legacy_client_import_map (
        legacy_id bigint primary key,
        new_id bigint not null unique
    ) on commit drop;
    create temporary table legacy_pet_import_map (
        legacy_id bigint primary key,
        new_id bigint not null unique
    ) on commit drop;
    create temporary table legacy_service_import_map (
        legacy_name varchar(100) primary key,
        new_id bigint not null
    ) on commit drop;
    create temporary table legacy_reservation_import_map (
        legacy_id bigint primary key,
        new_id bigint not null unique,
        service_id bigint not null,
        service_name varchar(100) not null
    ) on commit drop;

    select coalesce(max(id), 0) into next_client_id from clients;
    execute $sql$
        select coalesce(max(id_client)::bigint, 0)
        from legacy_clients
    $sql$ into legacy_max_id;
    next_client_id := greatest(next_client_id, legacy_max_id) + 1;

    for legacy_client in execute $sql$
        select
            id_client::bigint as legacy_id,
            name::text as name,
            email::text as email,
            phone::text as phone
        from legacy_clients
        order by id_client
    $sql$
    loop
        normalized_email := lower(nullif(btrim(legacy_client.email), ''));

        if normalized_email is null
            or length(normalized_email) > 254
            or normalized_email !~ '^[^[:space:]@]+@[^[:space:]@]+$'
            or exists (
                select 1 from users
                where email_normalized = normalized_email
            )
        then
            normalized_email :=
                'legacy-client-' || legacy_client.legacy_id
                || '@legacy.amidog.invalid';
            collision_suffix := 0;
            while exists (
                select 1 from users
                where email_normalized = normalized_email
            )
            loop
                collision_suffix := collision_suffix + 1;
                normalized_email :=
                    'legacy-client-' || legacy_client.legacy_id
                    || '-' || collision_suffix || '@legacy.amidog.invalid';
            end loop;
        end if;

        insert into users (
            email_normalized,
            password_hash,
            email_verified_at,
            account_type,
            enabled
        )
        values (
            normalized_email,
            null,
            null,
            'CLIENT',
            false
        )
        returning id into imported_user_id;

        if exists (
            select 1 from clients where id = legacy_client.legacy_id
        ) then
            while exists (
                select 1 from clients where id = next_client_id
            )
            loop
                next_client_id := next_client_id + 1;
            end loop;
            imported_client_id := next_client_id;
            next_client_id := next_client_id + 1;
        else
            imported_client_id := legacy_client.legacy_id;
        end if;

        insert into clients (
            id,
            user_id,
            name,
            phone,
            active
        )
        values (
            imported_client_id,
            imported_user_id,
            left(
                coalesce(
                    nullif(btrim(legacy_client.name), ''),
                    'Cliente legado ' || legacy_client.legacy_id
                ),
                120
            ),
            left(
                coalesce(
                    nullif(btrim(legacy_client.phone), ''),
                    'Sin teléfono'
                ),
                30
            ),
            true
        );

        insert into legacy_client_import_map(legacy_id, new_id)
        values (legacy_client.legacy_id, imported_client_id);
    end loop;

    if to_regclass('legacy_pets') is not null then
        select coalesce(max(id), 0) into next_pet_id from pets;
        execute $sql$
            select coalesce(max(id_pet)::bigint, 0)
            from legacy_pets
        $sql$ into legacy_max_id;
        next_pet_id := greatest(next_pet_id, legacy_max_id) + 1;

        for legacy_pet in execute $sql$
            select
                id_pet::bigint as legacy_id,
                id_client::bigint as legacy_client_id,
                name::text as name,
                species::text as species,
                breed::text as breed,
                birthdate
            from legacy_pets
            order by id_pet
        $sql$
        loop
            select new_id into mapped_client_id
            from legacy_client_import_map
            where legacy_id = legacy_pet.legacy_client_id;

            if mapped_client_id is null then
                continue;
            end if;

            if exists (select 1 from pets where id = legacy_pet.legacy_id) then
                while exists (
                    select 1 from pets where id = next_pet_id
                )
                loop
                    next_pet_id := next_pet_id + 1;
                end loop;
                imported_pet_id := next_pet_id;
                next_pet_id := next_pet_id + 1;
            else
                imported_pet_id := legacy_pet.legacy_id;
            end if;

            insert into pets (
                id,
                client_id,
                name,
                species,
                breed,
                birthdate,
                active
            )
            values (
                imported_pet_id,
                mapped_client_id,
                left(
                    coalesce(
                        nullif(btrim(legacy_pet.name), ''),
                        'Mascota legada ' || legacy_pet.legacy_id
                    ),
                    80
                ),
                left(
                    coalesce(
                        nullif(btrim(legacy_pet.species), ''),
                        'Sin especificar'
                    ),
                    40
                ),
                left(nullif(btrim(legacy_pet.breed), ''), 80),
                legacy_pet.birthdate,
                true
            );

            insert into legacy_pet_import_map(legacy_id, new_id)
            values (legacy_pet.legacy_id, imported_pet_id);
        end loop;
    end if;

    if to_regclass('legacy_reservations') is not null then
        for legacy_service in execute $sql$
            select distinct
                left(
                    coalesce(
                        nullif(btrim(service::text), ''),
                        'Servicio legado'
                    ),
                    100
                ) as service_name
            from legacy_reservations
            order by service_name
        $sql$
        loop
            legacy_service_name := legacy_service.service_name;
            imported_service_id := null;

            select id into imported_service_id
            from services
            where name = legacy_service_name
            order by id
            limit 1;

            if imported_service_id is null then
                base_service_code :=
                    'legacy-' || md5(legacy_service_name);
                service_code := base_service_code;
                collision_suffix := 0;

                while exists (
                    select 1 from services
                    where code = service_code
                      and name <> legacy_service_name
                )
                loop
                    collision_suffix := collision_suffix + 1;
                    service_code :=
                        left(base_service_code, 48)
                        || '-' || lpad(collision_suffix::text, 4, '0');
                end loop;

                insert into services(code, name, active)
                values (service_code, legacy_service_name, true)
                returning id into imported_service_id;
            end if;

            insert into legacy_service_import_map(legacy_name, new_id)
            values (legacy_service_name, imported_service_id);
        end loop;

        select coalesce(max(id), 0)
        into next_reservation_id
        from reservations;
        execute $sql$
            select coalesce(max(id_reservation)::bigint, 0)
            from legacy_reservations
        $sql$ into legacy_max_id;
        next_reservation_id :=
            greatest(next_reservation_id, legacy_max_id) + 1;

        -- Greedy chronological import handles exact duplicate slots and
        -- arbitrary non-identical overlaps. The earliest non-conflicting
        -- occupied row remains occupied; each later collision is retained as
        -- cancelled so the V9 exclusion can always be installed safely.
        for legacy_reservation in execute $sql$
            select
                id_reservation::bigint as legacy_id,
                id_client::bigint as legacy_client_id,
                reservation_date,
                reservation_time,
                service::text as service,
                coalesce(confirmed, false) as confirmed
            from legacy_reservations
            where reservation_date is not null
              and reservation_time is not null
            order by reservation_date, reservation_time, id_reservation
        $sql$
        loop
            select new_id into mapped_client_id
            from legacy_client_import_map
            where legacy_id = legacy_reservation.legacy_client_id;

            if mapped_client_id is null then
                continue;
            end if;

            legacy_service_name := left(
                coalesce(
                    nullif(btrim(legacy_reservation.service), ''),
                    'Servicio legado'
                ),
                100
            );
            select new_id into imported_service_id
            from legacy_service_import_map
            where legacy_name = legacy_service_name;

            -- PostgreSQL resolves a nonexistent local timestamp with the
            -- pre-gap offset and an ambiguous timestamp with the post-overlap
            -- offset (its standard-time preference). V8 intentionally keeps
            -- that deterministic legacy-import behavior. Runtime ClinicTime
            -- validation can apply a stricter reject/normalize policy to new
            -- user input without changing imported historical instants.
            legacy_start :=
                (legacy_reservation.reservation_date
                    + legacy_reservation.reservation_time)
                at time zone 'America/Santiago';
            desired_status := case
                when legacy_reservation.confirmed then 'CONFIRMED'
                else 'PENDING'
            end;

            select exists (
                select 1
                from reservations
                where status in ('PENDING', 'CONFIRMED')
                  and tstzrange(
                        scheduled_start, scheduled_end, '[)'
                      ) && tstzrange(
                        legacy_start,
                        legacy_start + interval '30 minutes',
                        '[)'
                      )
            ) into collides;
            imported_status := case
                when collides then 'CANCELLED'
                else desired_status
            end;

            if exists (
                select 1 from reservations
                where id = legacy_reservation.legacy_id
            ) then
                while exists (
                    select 1 from reservations where id = next_reservation_id
                )
                loop
                    next_reservation_id := next_reservation_id + 1;
                end loop;
                imported_reservation_id := next_reservation_id;
                next_reservation_id := next_reservation_id + 1;
            else
                imported_reservation_id := legacy_reservation.legacy_id;
            end if;

            insert into reservations (
                id,
                client_id,
                scheduled_start,
                scheduled_end,
                status,
                cancelled_at,
                cancelled_by,
                cancellation_reason
            )
            values (
                imported_reservation_id,
                mapped_client_id,
                legacy_start,
                legacy_start + interval '30 minutes',
                imported_status,
                case when collides then now() else null end,
                case when collides then 'MIGRATION' else null end,
                case
                    when collides
                        then 'Conflicto detectado durante migración Phase 1'
                    else null
                end
            );

            insert into legacy_reservation_import_map (
                legacy_id,
                new_id,
                service_id,
                service_name
            )
            values (
                legacy_reservation.legacy_id,
                imported_reservation_id,
                imported_service_id,
                legacy_service_name
            );
        end loop;
    end if;

    if to_regclass('legacy_reservation_pets') is not null then
        for legacy_link in execute $sql$
            select
                id_reservation::bigint as legacy_reservation_id,
                id_pet::bigint as legacy_pet_id
            from legacy_reservation_pets
            order by id_reservation, id_pet
        $sql$
        loop
            mapped_reservation_id := null;
            mapped_pet_id := null;
            imported_service_id := null;
            legacy_service_name := null;

            select new_id, service_id, service_name
            into
                mapped_reservation_id,
                imported_service_id,
                legacy_service_name
            from legacy_reservation_import_map
            where legacy_id = legacy_link.legacy_reservation_id;

            select new_id into mapped_pet_id
            from legacy_pet_import_map
            where legacy_id = legacy_link.legacy_pet_id;

            if mapped_reservation_id is not null
                and mapped_pet_id is not null
                and exists (
                    select 1
                    from reservations r
                    join pets p on p.id = mapped_pet_id
                    where r.id = mapped_reservation_id
                      and r.client_id = p.client_id
                )
            then
                insert into reservation_items (
                    reservation_id,
                    pet_id,
                    service_id,
                    service_name_snapshot
                )
                values (
                    mapped_reservation_id,
                    mapped_pet_id,
                    imported_service_id,
                    legacy_service_name
                )
                on conflict (reservation_id, pet_id) do nothing;
            end if;
            -- A malformed cross-owner Phase 1 link remains available in
            -- legacy_reservation_pets for operator review. It is deliberately
            -- not attached to the new reservation aggregate.
        end loop;
    end if;

    -- Every identity touched by the import is left ready for the next runtime
    -- insert, including tables that contained only explicit legacy IDs.
    perform setval(
        pg_get_serial_sequence('users', 'id'),
        coalesce((select max(id) from users), 1),
        exists(select 1 from users)
    );
    perform setval(
        pg_get_serial_sequence('clients', 'id'),
        coalesce((select max(id) from clients), 1),
        exists(select 1 from clients)
    );
    perform setval(
        pg_get_serial_sequence('pets', 'id'),
        coalesce((select max(id) from pets), 1),
        exists(select 1 from pets)
    );
    perform setval(
        pg_get_serial_sequence('services', 'id'),
        coalesce((select max(id) from services), 1),
        exists(select 1 from services)
    );
    perform setval(
        pg_get_serial_sequence('reservations', 'id'),
        coalesce((select max(id) from reservations), 1),
        exists(select 1 from reservations)
    );
    perform setval(
        pg_get_serial_sequence('reservation_items', 'id'),
        coalesce((select max(id) from reservation_items), 1),
        exists(select 1 from reservation_items)
    );
end
$migration$;
