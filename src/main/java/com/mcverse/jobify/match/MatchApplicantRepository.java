package com.mcverse.jobify.match;

import com.mcverse.jobify.application.model.Application;
import com.mcverse.jobify.user.model.SeekerSkill;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** The applicants of one job and their skills, fetched in a fixed number of queries however many there are. */
public interface MatchApplicantRepository extends Repository<Application, String> {

    /** Applications that are still alive (not withdrawn), newest first. Page it to cap how many are ranked. */
    @EntityGraph(attributePaths = {"seeker"})
    @Query("""
            select a from Application a
            where a.job.postId = :jobId
              and a.status <> com.mcverse.jobify.application.model.ApplicationStatus.WITHDRAWN
            order by a.createdAt desc, a.id
            """)
    List<Application> activeForJob(@Param("jobId") int jobId, Pageable limit);

    @Query("select s from SeekerSkill s join fetch s.skill where s.seeker.id in :seekerIds")
    List<SeekerSkill> skillsOf(@Param("seekerIds") Collection<String> seekerIds);
}
