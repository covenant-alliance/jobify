-- Status history of applications: one row per step (created, moved by the employer, withdrawn, re-applied).
-- Only ever added to; it feeds the employer's hiring funnel (GET /employers/me/stats, "funnel").
-- Applications that existed before this migration get a minimal history from ApplicationHistoryBackfill at startup,
-- so no data is copied here.
create table application_status_changes (
    id             varchar(255) primary key,
    application_id varchar(255) not null,
    from_status    varchar(255),            -- null when the application was created
    to_status      varchar(255) not null,
    changed_at     timestamp(6) not null,
    changed_by     varchar(255),
    -- deleting an application (for example an approved account deletion) removes its history with it
    constraint fk_application_status_changes_application
        foreign key (application_id) references applications (id) on delete cascade
);

create index idx_application_status_changes_application on application_status_changes (application_id, changed_at);
