package com.mcverse.jobify.application.repository;

import com.mcverse.jobify.application.model.ApplicationStatus;
import com.mcverse.jobify.application.model.ApplicationStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ApplicationStatusChangeRepository extends JpaRepository<ApplicationStatusChange, String> {

    /** A history row reduced to what the funnel needs, so no entity graph is loaded. */
    record Row(String applicationId, ApplicationStatus from, ApplicationStatus to, LocalDateTime at) {}

    /** The whole history of every application to any job of one employer, oldest change first. */
    @Query("""
            select new com.mcverse.jobify.application.repository.ApplicationStatusChangeRepository$Row(
                       c.application.id, c.fromStatus, c.toStatus, c.changedAt)
            from ApplicationStatusChange c
            where c.application.job.employer.username = :employerUsername
            order by c.changedAt, c.id
            """)
    List<Row> findRowsByEmployer(String employerUsername);

    /** Same, for every job the person or anyone in their company posted. */
    @Query("""
            select new com.mcverse.jobify.application.repository.ApplicationStatusChangeRepository$Row(
                       c.application.id, c.fromStatus, c.toStatus, c.changedAt)
            from ApplicationStatusChange c
            where c.application.job.employer.username = :username
               or exists (select 1 from Employer me where me.username = :username
                          and me.company is not null and me.company = c.application.job.employer.company)
            order by c.changedAt, c.id
            """)
    List<Row> findRowsManagedBy(@org.springframework.data.repository.query.Param("username") String username);

    long countByApplicationId(String applicationId);

    List<ApplicationStatusChange> findAllByApplicationIdOrderByChangedAtAscIdAsc(String applicationId);
}
