package com.mcverse.jobify.application.repository;

import com.mcverse.jobify.application.model.Application;
import com.mcverse.jobify.application.model.ApplicationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, String> {

    Optional<Application> findBySeekerUsernameAndJobPostId(String seekerUsername, Integer postId);

    List<Application> findAllBySeekerUsernameOrderByCreatedAtDesc(String seekerUsername);

    List<Application> findAllByJobPostIdOrderByCreatedAtDesc(Integer postId);

    /** Every application to a job the person posted or anyone in their company posted, for the statistics. */
    @EntityGraph(attributePaths = "job")
    @Query("""
            select a from Application a
            where a.job.employer.username = :username
               or exists (select 1 from Employer me where me.username = :username
                          and me.company is not null and me.company = a.job.employer.company)
            """)
    List<Application> findAllManagedBy(@Param("username") String username);

    /** Every application to any job of one employer, for their statistics. */
    @EntityGraph(attributePaths = "job")
    List<Application> findAllByJobEmployerUsername(String employerUsername);

    List<Application> findAllByJobPostIdAndStatusOrderByCreatedAtDesc(Integer postId, ApplicationStatus status);

    @Query("select a.status, count(a) from Application a group by a.status")
    List<Object[]> countGroupedByStatus();

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    /** Applications that have no history yet: the ones that existed before history was recorded. */
    @Query("select a from Application a where not exists "
            + "(select 1 from ApplicationStatusChange c where c.application = a)")
    List<Application> findAllWithoutHistory();

    /** Needed before a seeker account is deleted, otherwise the foreign key blocks it. */
    @Modifying
    @Query("delete from Application a where a.seeker in (select s from Seeker s where s.username = :username)")
    int deleteBySeekerUsername(String username);

    /** Needed before an employer account (and so their jobs) is deleted. */
    @Modifying
    @Query("delete from Application a where a.job in (select j from JobPost j where j.employer.username = :username)")
    int deleteByJobEmployerUsername(String username);
}
