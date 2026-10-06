-- Structured responsibilities and requirements on jobs (#36): short sentences in display order.
create table job_responsibilities (
    job_post_id    integer not null,
    position       integer not null,
    responsibility varchar(300) not null,
    primary key (job_post_id, position),
    constraint fk_job_responsibilities_job foreign key (job_post_id) references job_posts (post_id) on delete cascade
);

create table job_requirements (
    job_post_id integer not null,
    position    integer not null,
    requirement varchar(300) not null,
    primary key (job_post_id, position),
    constraint fk_job_requirements_job foreign key (job_post_id) references job_posts (post_id) on delete cascade
);
