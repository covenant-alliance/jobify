-- Plain text of the job description (HTML tags removed), so server-side job search (GET /jobs/search) matches words and
-- never markup. Written by the application whenever a description is saved; jobs that already exist are filled in
-- once at startup by JobSearchTextBackfill (idempotent), so no data is copied here.
alter table job_posts add column description_text text;
