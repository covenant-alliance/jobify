-- A catalogue of places learned from the jobs' free-text locations (#37). Jobs keep their text; location_id is
-- the clean value behind it, filled when a job is saved and, for older jobs, by LocationBackfill at startup.
create table locations (
    id           varchar(255) primary key,
    lookup_key   varchar(255) not null,
    city_key     varchar(255),
    display_name varchar(255) not null,
    city         varchar(255),
    region       varchar(255),
    country_code varchar(2),
    remote       boolean not null,
    constraint uk_locations_lookup_key unique (lookup_key)
);

create index idx_locations_city_key on locations (city_key);

alter table job_posts add column location_id varchar(255);
alter table job_posts add constraint fk_job_posts_location foreign key (location_id) references locations (id);
create index idx_job_posts_location on job_posts (location_id);
