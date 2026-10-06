package com.mcverse.jobify.auth.repository;

import com.mcverse.jobify.auth.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mcverse.jobify.auth.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);

    long countByRole(Role role);

    /**
     * Accounts of the given roles whose username, or whose profile first or last name, contains the pattern.
     * The pattern is lower case and already escaped, with backslash as the escape character.
     */
    @Query("""
            select u from AppUser u
            where u.role in :roles
              and (lower(u.username) like :pattern escape '\\'
                or exists (select 1 from Seeker s where s.username = u.username
                           and (lower(s.name) like :pattern escape '\\' or lower(s.lastName) like :pattern escape '\\'))
                or exists (select 1 from Employer e where e.username = u.username
                           and (lower(e.name) like :pattern escape '\\' or lower(e.lastName) like :pattern escape '\\')))
            """)
    Page<AppUser> search(Collection<Role> roles, String pattern, Pageable pageable);
}
