-- job_rating was removed from the API and the entity (it only ever held 0). Dropping the column loses no information.
-- hourly_rate is NOT dropped here: the API still returns hourlyRate and the front end falls back to it.
alter table job_posts drop column job_rating;
