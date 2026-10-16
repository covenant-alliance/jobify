package com.mcverse.jobify.match;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.user.service.CompanyAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Who may see the candidates of a job: whoever posted it, and anyone in the company that did (one rule, in {@link CompanyAccess}). */
@Service
public class MatchJobAccess {

    private static final Logger log = LoggerFactory.getLogger(MatchJobAccess.class);

    private final JobRepo jobRepo;
    private final CompanyAccess access;
    private final MatchFactsLoader facts;
    private final AuditLog auditLog;

    public MatchJobAccess(JobRepo jobRepo, CompanyAccess access, MatchFactsLoader facts, AuditLog auditLog) {
        this.jobRepo = jobRepo;
        this.access = access;
        this.facts = facts;
        this.auditLog = auditLog;
    }

    /** The job as the matching service sees it, once the caller is known to manage it. 404 if there is no such job. */
    @Transactional(readOnly = true)
    public JobFacts managedJob(String username, int jobId) {
        JobPost job = jobRepo.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("JobPost", Integer.toString(jobId)));
        if (!access.canManageDataOf(username, job.getEmployer())) {
            log.warn("Denied: user '{}' asked for the candidates of job {} of another company", username, jobId);
            auditLog.event(username, "ACCESS_DENIED", "action='see candidates' job=" + jobId + " reason=not the owning company");
            throw new LicenseValidationException("Only the company that posted this job can see its candidates.");
        }
        return facts.job(job);
    }
}
