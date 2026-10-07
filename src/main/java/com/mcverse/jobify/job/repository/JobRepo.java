package com.mcverse.jobify.job.repository;

import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.user.model.Employer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Gives every job of one employer to another (a leaving team member's jobs stay with the company). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update JobPost j set j.employer = :to where j.employer = :from")
    int reassign(@Param("from") Employer from, @Param("to") Employer to);

    /** Jobs the person posted plus every job posted by anyone in their company. */
    @Query("""
            select j from JobPost j
            where j.employer.username = :username
               or exists (select 1 from Employer me where me.username = :username
                          and me.company is not null and me.company = j.employer.company)
            """)
    List<JobPost> findAllManagedBy(@Param("username") String username);

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
