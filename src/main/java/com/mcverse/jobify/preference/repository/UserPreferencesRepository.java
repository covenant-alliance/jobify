package com.mcverse.jobify.preference.repository;

import com.mcverse.jobify.preference.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, String> {}
