-- Benefits and pictures on jobs, and a logo on companies. Files live under the upload folder; rows say where.
alter table companies add column logo_path varchar(255);
alter table companies add column logo_content_type varchar(100);
alter table companies add column logo_version bigint;

create table job_benefits (
    job_post_id integer not null,
    position    integer not null,
    benefit     varchar(80) not null,
    primary key (job_post_id, position),
    constraint fk_job_benefits_job foreign key (job_post_id) references job_posts (post_id) on delete cascade
);

create table job_images (
    id          varchar(255) primary key,
    job_post_id integer not null,
    file_path   varchar(255) not null,
    content_type varchar(100) not null,
    position    integer not null,
    created_at  timestamp(6) not null,
    constraint fk_job_images_job foreign key (job_post_id) references job_posts (post_id) on delete cascade
);

create index idx_job_images_job on job_images (job_post_id, position);
