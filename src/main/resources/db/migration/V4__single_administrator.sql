create unique index users_single_admin_idx
    on users ((account_type))
    where account_type = 'ADMIN';
