package com.mcverse.jobify.user.repository;

import com.mcverse.jobify.user.model.CompanyActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyActivityRepository extends JpaRepository<CompanyActivity, String> {

    List<CompanyActivity> findByCompanyIdOrderByCreatedAtDescIdDesc(String companyId, Pageable page);
}
