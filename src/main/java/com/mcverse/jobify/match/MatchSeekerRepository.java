package com.mcverse.jobify.match;

import com.mcverse.jobify.user.model.Seeker;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Which seekers are worth scoring for a new job: a bounded, cheap SQL pre-selection, as for recommended jobs. */
public interface MatchSeekerRepository extends Repository<Seeker, String> {

    /**
     * Ids of seekers who have at least one of the job's required skills, have not applied to it, and either did not
     * save a country preference, or the job is remote, or the job's country is one of theirs. Page it to cap how many
     * are scored.
     */
    @Query("""
            select distinct s.id from Seeker s join SeekerSkill ss on ss.seeker = s
            where ss.skill.id in :skillIds
              and not exists (select 1 from Application a where a.seeker = s and a.job.postId = :jobId)
              and (:remote = true
                   or not exists (select 1 from SeekerJobPreferences p where p.seekerId = s.id and size(p.countryCodes) > 0)
                   or exists (select 1 from SeekerJobPreferences p2 join p2.countryCodes c
                              where p2.seekerId = s.id and c = :country))
            order by s.id
            """)
    List<String> interestedIn(@Param("jobId") int jobId, @Param("skillIds") Collection<String> skillIds,
                              @Param("remote") boolean remote, @Param("country") String country, Pageable limit);

    @Query("select s from Seeker s where s.id in :ids")
    List<Seeker> findAllByIds(@Param("ids") Collection<String> ids);
}
