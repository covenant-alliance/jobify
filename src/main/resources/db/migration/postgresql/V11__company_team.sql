-- Company teams (#72, #73): when a person joined their company, invitations, and the company activity feed.
alter table employers add column company_joined_at timestamp(6);
update employers set company_joined_at = (select u.creation_date from users u where u.id = employers.id)
 where company_id is not null;

create table company_invitations (
    id               varchar(255) primary key,
    company_id       varchar(255) not null,
    invitee_username varchar(255) not null,
    invited_by       varchar(255) not null,
    status           varchar(20) not null,
    created_at       timestamp(6) not null,
    expires_at       timestamp(6) not null,
    responded_at     timestamp(6),
    constraint fk_company_invitations_company foreign key (company_id) references companies (id)
);
create index idx_company_invitations_invitee on company_invitations (invitee_username, status);
create index idx_company_invitations_company on company_invitations (company_id, status);

create table company_activity (
    id             varchar(255) primary key,
    company_id     varchar(255) not null,
    actor          varchar(255) not null,
    type           varchar(40) not null,
    summary        varchar(400) not null,
    job_id         integer,
    application_id varchar(255),
    created_at     timestamp(6) not null
);
create index idx_company_activity_company_created on company_activity (company_id, created_at);
