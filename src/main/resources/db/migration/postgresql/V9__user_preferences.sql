-- Per-user settings (#25): the accent colour, and which notification types the user switched off.
-- Only differences from the defaults are stored (every type is on unless listed). Keyed by username, as
-- notifications are; AdminAccountService removes the rows when an account is deleted.
create table user_preferences (
    username     varchar(255) primary key,
    accent_color varchar(7),
    updated_at   timestamp(6) not null
);

create table user_disabled_notifications (
    username          varchar(255) not null,
    notification_type varchar(255) not null,
    primary key (username, notification_type),
    constraint fk_user_disabled_notifications_user
        foreign key (username) references user_preferences (username) on delete cascade
);
