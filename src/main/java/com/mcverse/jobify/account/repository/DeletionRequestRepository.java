package com.mcverse.jobify.account.repository;

import com.mcverse.jobify.account.model.DeletionRequest;
import com.mcverse.jobify.account.model.DeletionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeletionRequestRepository extends JpaRepository<DeletionRequest, String> {
    Optional<DeletionRequest> findFirstByUsernameAndStatusOrderByRequestedAtDesc(String username, DeletionRequestStatus status);
    List<DeletionRequest> findAllByStatusOrderByRequestedAtAsc(DeletionRequestStatus status);

    long countByStatus(DeletionRequestStatus status);
}
