package com.mcverse.jobify.job.repository;

import com.mcverse.jobify.model.JobPost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface JobRepo extends JpaRepository<JobPost, Integer> {
    @Override
    @EntityGraph(attributePaths = {"employer", "employer.company"})
    List<JobPost> findAll();

    @EntityGraph(attributePaths = {"employer", "employer.company"})
    List<JobPost> findAllByAvailable(boolean available);

    List<JobPost> findAllByEmployerUsername(String username);

    long countByAvailable(boolean available);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    /** Gives rows created before the compensation model a rate: the old hourly rate, as HOURLY. */
    @Modifying
    @Query("update JobPost j set j.rate = j.hourlyRate, j.rateType = com.mcverse.jobify.job.model.RateType.HOURLY "
            + "where j.rate is null or j.rateType is null")
    int backfillCompensation();
}
