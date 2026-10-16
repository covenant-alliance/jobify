package com.mcverse.jobify.match;

import com.mcverse.jobify.job.model.JobPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Narrowing in SQL before anything is sent to the matching service: ranking every job over the wire per request is
 * the mistake this design avoids. Lives in the match slice because it needs both jobs and applications, and neither
 * of those slices may depend on the other's repository in that direction.
 */
public interface MatchCandidateRepository extends Repository<JobPost, Integer> {

    /**
     * Ids of open jobs the seeker has not applied to, newest first. With {@code anyCountry} false a job must be remote or
     * in one of {@code countries}. Page it to cap how many are sent.
     */
    @Query("""
            select j.postId from JobPost j left join j.locationRef l
            where j.available = true
              and not exists (select 1 from Application a where a.job = j and a.seeker.username = :username)
              and (:anyCountry = true
                   or j.workMode = com.mcverse.jobify.job.model.WorkMode.REMOTE
                   or l.countryCode in :countries)
            order by j.createdAt desc, j.postId desc
            """)
    List<Integer> narrowForSeeker(@Param("username") String username, @Param("anyCountry") boolean anyCountry,
                                  @Param("countries") Collection<String> countries, Pageable limit);

    /** The chosen jobs with everything the facts and the response need, in one query. */
    @EntityGraph(attributePaths = {"employer", "employer.company", "requiredSkills", "locationRef"})
    List<JobPost> findAllByPostIdIn(Collection<Integer> postIds);
}
