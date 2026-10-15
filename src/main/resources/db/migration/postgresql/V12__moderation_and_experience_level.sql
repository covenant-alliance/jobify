-- Administrators can disable an account (no login, tokens stop working); jobs can state an experience level.
alter table app_users add column enabled boolean not null default true;
alter table job_posts add column experience_level varchar(20);
