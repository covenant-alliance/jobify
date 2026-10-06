package com.mcverse.jobify.user.repository;

import com.mcverse.jobify.user.model.Seeker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeekerRepository extends JpaRepository<Seeker, String> {
    Optional<Seeker> findByUsername(String username);

    List<Seeker> findAllByUsernameIn(Collection<String> usernames);

    long countByCreationDateGreaterThanEqual(LocalDateTime since);
}
