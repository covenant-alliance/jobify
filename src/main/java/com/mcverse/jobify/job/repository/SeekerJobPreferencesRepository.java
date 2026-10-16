package com.mcverse.jobify.job.repository;

import com.mcverse.jobify.job.model.SeekerJobPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeekerJobPreferencesRepository extends JpaRepository<SeekerJobPreferences, String> {
}
