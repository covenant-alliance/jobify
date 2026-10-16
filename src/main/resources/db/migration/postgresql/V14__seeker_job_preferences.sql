-- What a seeker is looking for (#104): countries, work modes, employment types, pay range with its unit, experience level.
-- Replaces the old job_preferences table, which no endpoint ever read or wrote (free-text location, no rate unit,
-- experience level as free text), so it holds no data worth carrying over.
create table seeker_job_preferences (
    seeker_id           varchar(255) primary key,
    preferred_job_title varchar(100),
    min_rate            double precision,
    max_rate            double precision,
    rate_type           varchar(255),
    experience_level    varchar(255),
    updated_at          timestamp(6) not null,
    constraint fk_seeker_job_preferences_seeker foreign key (seeker_id) references seekers (id)
);

create table seeker_preference_countries (
    seeker_id    varchar(255) not null,
    country_code varchar(2)   not null,
    primary key (seeker_id, country_code),
    constraint fk_seeker_pref_countries foreign key (seeker_id) references seeker_job_preferences (seeker_id) on delete cascade
);

create table seeker_preference_work_modes (
    seeker_id varchar(255) not null,
    work_mode varchar(255) not null,
    primary key (seeker_id, work_mode),
    constraint fk_seeker_pref_work_modes foreign key (seeker_id) references seeker_job_preferences (seeker_id) on delete cascade
);

create table seeker_preference_employment_types (
    seeker_id       varchar(255) not null,
    employment_type varchar(255) not null,
    primary key (seeker_id, employment_type),
    constraint fk_seeker_pref_employment_types foreign key (seeker_id) references seeker_job_preferences (seeker_id) on delete cascade
);

alter table seekers drop constraint fk_seekers_job_preferences;
alter table seekers drop constraint uk_seekers_job_preferences;
alter table seekers drop column job_preferences_id;
drop table job_preferences;
