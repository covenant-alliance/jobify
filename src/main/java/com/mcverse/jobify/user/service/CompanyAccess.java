package com.mcverse.jobify.user.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.user.model.CompanyRole;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.repository.EmployerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place that decides who may act on a company's data. A job (and everything hanging off it: its images,
 * applications, statistics) can be managed by the person who posted it and by everyone connected to the same company.
 * An employer without a company is a company of one, so only they can.
 */
@Service
public class CompanyAccess {

    private final EmployerRepository employers;
    private final AuditLog auditLog;

    public CompanyAccess(EmployerRepository employers, AuditLog auditLog) {
        this.employers = employers;
        this.auditLog = auditLog;
    }

    /** True when {@code poster} (who posted a job) is {@code username}, or works for the same company as them. */
    @Transactional(readOnly = true)
    public boolean canManageDataOf(String username, Employer poster) {
        if (poster == null) {
            return false;
        }
        if (poster.getUsername().equals(username)) {
            return true;
        }
        if (poster.getCompany() == null) {
            return false;
        }
        return employers.findByUsername(username).map(Employer::getCompany)
                .map(company -> company.getId().equals(poster.getCompany().getId())).orElse(false);
    }

    /** Only the company's owner may continue; a manager is told so. */
    public void requireOwner(String username, Employer caller) {
        if (caller.getCompanyRole() != CompanyRole.OWNER) {
            auditLog.event(username, "ACCESS_DENIED", "action='change company' role=" + caller.getCompanyRole());
            throw new LicenseValidationException("Only the company owner can do that.");
        }
    }
}
