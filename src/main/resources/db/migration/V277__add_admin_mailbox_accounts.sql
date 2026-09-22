create table admin_mailbox_accounts (
    id bigserial primary key,
    email_address varchar(320) not null unique,
    display_name varchar(160),
    username varchar(320) not null,
    password_encrypted text,
    imap_host varchar(255) not null,
    imap_port integer not null default 993,
    imap_ssl boolean not null default true,
    smtp_host varchar(255) not null,
    smtp_port integer not null default 465,
    smtp_ssl boolean not null default true,
    enabled boolean not null default false,
    last_connection_status varchar(32) not null default 'CONFIG_REQUIRED',
    last_connection_error varchar(500),
    last_tested_at timestamp with time zone,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint ck_admin_mailbox_imap_port check (imap_port between 1 and 65535),
    constraint ck_admin_mailbox_smtp_port check (smtp_port between 1 and 65535)
);

insert into admin_mailbox_accounts
    (email_address, display_name, username, imap_host, imap_port, imap_ssl, smtp_host, smtp_port, smtp_ssl)
values
    ('aws@gruncalorietracker.com', 'AWS', 'aws@gruncalorietracker.com', 'ni-kyrenia.guzelhosting.com', 993, true, 'ni-kyrenia.guzelhosting.com', 465, true),
    ('info@gruncalorietracker.com', 'Info', 'info@gruncalorietracker.com', 'ni-kyrenia.guzelhosting.com', 993, true, 'ni-kyrenia.guzelhosting.com', 465, true),
    ('support@gruncalorietracker.com', 'Support', 'support@gruncalorietracker.com', 'ni-kyrenia.guzelhosting.com', 993, true, 'ni-kyrenia.guzelhosting.com', 465, true)
on conflict (email_address) do nothing;
