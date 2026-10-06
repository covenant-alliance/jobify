package com.mcverse.jobify.job.repository;

import com.mcverse.jobify.job.model.JobPost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
