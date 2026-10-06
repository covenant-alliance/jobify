package com.mcverse.jobify.job.repository;

import com.mcverse.jobify.job.model.JobPost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface JobRepo extends JpaRepository<JobPost, Integer>, JpaSpecificationExecutor<JobPost> {
    @Override
    @EntityGraph(attributePaths = {"employer", "employer.company"})
    List<JobPost> findAll();

    @EntityGraph(attributePaths = {"employer", "employer.company"})
    List<JobPost> findAllByAvailable(boolean available);

    List<JobPost> findAllByEmployerUsername(String username);

    /** The jobs of one search page with everything a response needs, in one query instead of one per job. */
    @EntityGraph(attributePaths = {"employer", "employer.company", "requiredSkills"})
    List<JobPost> findAllByPostIdIn(Collection<Integer> postIds);

    /** Jobs saved before the plain-text description column existed. */
    List<JobPost> findAllByDescriptionTextIsNull();

    /** Jobs that have location text but no resolved place yet (jobs from before places existed). */
    List<JobPost> findAllByLocationRefIsNullAndLocationIsNotNullOrderByPostId();

    long countByAvailable(boolean available);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);
}
