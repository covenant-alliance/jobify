package com.mcverse.jobify.job.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.job.dto.CreateJobRequest;
import com.mcverse.jobify.job.dto.JobPostResponse;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.model.Skill;
import com.mcverse.jobify.user.repository.EmployerRepository;
import com.mcverse.jobify.user.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    @Autowired
    private JobRepo repo;

    @Autowired
    private EmployerRepository employerRepo;

    @Autowired
    private SkillRepository skillRepo;

    @Autowired
    private HtmlSanitizer htmlSanitizer;

    @Autowired
    private JobResponseMapper mapper;

    @Autowired
    private AuditLog auditLog;

    @Transactional(readOnly = true)
    public List<JobPostResponse> getJobs(Boolean available) {
        List<JobPost> posts = (available != null)
                ? repo.findAllByAvailable(available)
                : repo.findAll();
        return posts.stream().map(mapper::toResponse).toList();
    }

    /** Every job owned by the employer, open and closed. */
    @Transactional(readOnly = true)
    public List<JobPostResponse> getJobsOwnedBy(String employerUsername) {
        requireEmployer(employerUsername, "view your job postings");
        return repo.findAllByEmployerUsername(employerUsername).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobPostResponse getJobById(Integer id) {
        return repo.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("JobPost", id.toString()));
    }

    @Transactional
    public JobPostResponse addJob(CreateJobRequest request, String employerUsername) {
        Employer employer = requireEmployer(employerUsername, "post jobs");
        JobPost job = new JobPost(request.jobTitle().trim(), htmlSanitizer.sanitize(request.jobDescription()),
                request.rate(), request.rateType());
        job.setEmployer(employer);
        applyEditableFields(job, request);
        return mapper.toResponse(repo.save(job));
    }

    @Transactional
    public JobPostResponse updateJob(Integer id, CreateJobRequest request, String username) {
        JobPost job = findOwnedJob(id, username);
        job.setJobTitle(request.jobTitle().trim());
        job.setJobDescription(htmlSanitizer.sanitize(request.jobDescription()));
        job.setCompensation(request.rate(), request.rateType());
        applyEditableFields(job, request);
        return mapper.toResponse(repo.save(job));
    }

    @Transactional
    public JobPostResponse updateAvailability(Integer id, boolean available, String username) {
        JobPost job = findOwnedJob(id, username);
        job.setAvailable(available);
        return mapper.toResponse(repo.save(job));
    }

    private void applyEditableFields(JobPost job, CreateJobRequest request) {
        job.setLocation(request.location());
        job.setWorkMode(request.workMode());
        job.setEmploymentType(request.employmentType());
        job.setRequiredSkills(resolveSkills(request.requiredSkills()));
        if (request.benefits() != null) { // omitted on edit = unchanged, so clients that predate benefits lose nothing
            job.setBenefits(request.benefits().stream().map(String::trim).toList());
        }
        if (request.responsibilities() != null) {
            job.setResponsibilities(request.responsibilities().stream().map(String::trim).toList());
        }
        if (request.requirements() != null) {
            job.setRequirements(request.requirements().stream().map(String::trim).toList());
        }
    }

    private Employer requireEmployer(String username, String action) {
        return employerRepo.findByUsername(username).orElseThrow(() -> {
            log.warn("Denied: user '{}' tried to {} without the EMPLOYER role", username, action);
            auditLog.event(username, "ACCESS_DENIED", "action='" + action + "' reason=not an employer");
            return new LicenseValidationException("Only employers can " + action + ".");
        });
    }

    /** Loads a job and checks that the caller is the employer who posted it. */
    public JobPost findOwnedJob(Integer id, String username) {
        JobPost job = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JobPost", id.toString()));
        Employer owner = job.getEmployer();
        if (owner == null || !owner.getUsername().equals(username)) {
            log.warn("Denied: user '{}' tried to modify job {} owned by '{}'", username, id,
                    owner == null ? "nobody" : owner.getUsername());
            auditLog.event(username, "ACCESS_DENIED", "action='modify job' job=" + id + " owner="
                    + (owner == null ? "nobody" : owner.getUsername()));
            throw new LicenseValidationException("You can only change job postings that you created.");
        }
        return job;
    }

    private List<Skill> resolveSkills(List<String> names) {
        if (names == null) {
            return new java.util.ArrayList<>();
        }
        return names.stream()
                .map(String::trim)
                .map(name -> skillRepo.findByNameIgnoreCase(name)
                        .orElseGet(() -> skillRepo.save(new Skill(name, null))))
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }
}
