package com.mcverse.jobify.job.repository;

import com.mcverse.jobify.job.model.SavedJob;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SavedJobRepository extends JpaRepository<SavedJob, String> {

    Optional<SavedJob> findBySeekerUsernameAndJobPostId(String seekerUsername, Integer postId);

    boolean existsBySeekerUsernameAndJobPostId(String seekerUsername, Integer postId);

    @EntityGraph(attributePaths = {"job", "job.employer", "job.employer.company"})
    List<SavedJob> findAllBySeekerUsernameOrderBySavedAtDesc(String seekerUsername);

    /** Needed before a seeker account is deleted, otherwise the foreign key blocks it. */
    @Modifying
    @Query("delete from SavedJob s where s.seeker in (select k from Seeker k where k.username = :username)")
    int deleteBySeekerUsername(String username);

    /** Needed before an employer account (and so their jobs) is deleted. */
    @Modifying
    @Query("delete from SavedJob s where s.job in (select j from JobPost j where j.employer.username = :username)")
    int deleteByJobEmployerUsername(String username);
}
