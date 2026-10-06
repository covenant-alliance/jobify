package com.mcverse.jobify.user.repository;

import com.mcverse.jobify.user.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, String> {
    Optional<Skill> findByNameIgnoreCase(String name);
}
