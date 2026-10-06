-- Pay is stored as rate + rate_type. hourly_rate was only a derived copy (rate converted to an hourly amount) and the
-- front end no longer reads it, so the column and the response field are retired (issue #55).
--
-- Safety order: first make sure no row loses its price. Any row without rate/rate_type (rows from before the
-- compensation model; there are none on a database created by this application's migrations) takes its old
-- hourly_rate as rate with type HOURLY, exactly as the old startup backfill did. Only then are rate and rate_type
-- made NOT NULL, which fails loudly (and rolls this migration back) if some row still lacks a value.
update job_posts set rate = hourly_rate, rate_type = 'HOURLY' where rate is null or rate_type is null;

alter table job_posts alter column rate set not null;
alter table job_posts alter column rate_type set not null;
alter table job_posts drop column hourly_rate;
