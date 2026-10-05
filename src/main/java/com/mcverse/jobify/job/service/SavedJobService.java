package com.mcverse.jobify.job.service;

import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.job.dto.JobPostResponse;
import com.mcverse.jobify.job.model.SavedJob;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.job.repository.SavedJobRepository;
import com.mcverse.jobify.model.JobPost;
import com.mcverse.jobify.user.model.Seeker;
import com.mcverse.jobify.user.repository.SeekerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SavedJobService {

    private static final Logger log = LoggerFactory.getLogger(SavedJobService.class);

    private final SavedJobRepository savedJobRepo;
    private final JobRepo jobRepo;
    private final SeekerRepository seekerRepo;
    private final JobResponseMapper mapper;

    public SavedJobService(SavedJobRepository savedJobRepo, JobRepo jobRepo, SeekerRepository seekerRepo,
                           JobResponseMapper mapper) {
        this.savedJobRepo = savedJobRepo;
        this.jobRepo = jobRepo;
        this.seekerRepo = seekerRepo;
        this.mapper = mapper;
    }

    /** Idempotent: saving a job that is already saved changes nothing. Closed jobs may be saved. */
    @Transactional
    public void save(Integer jobId, String username) {
        Seeker seeker = requireSeeker(username, "save jobs");
        JobPost job = findJob(jobId);
        if (savedJobRepo.existsBySeekerUsernameAndJobPostId(username, jobId)) {
            return;
        }
        try {
            savedJobRepo.saveAndFlush(new SavedJob(seeker, job));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request saved the same job first; the end state is what the caller wanted.
            log.debug("Job {} was already saved by '{}'", jobId, username);
        }
    }

    /** Idempotent: removing a job that is not saved is not an error. */
    @Transactional
    public void unsave(Integer jobId, String username) {
        requireSeeker(username, "manage saved jobs");
        findJob(jobId);
        savedJobRepo.findBySeekerUsernameAndJobPostId(username, jobId).ifPresent(savedJobRepo::delete);
    }

    /** Saved jobs, most recently saved first. Closed jobs stay in the list, flagged available = false. */
    @Transactional(readOnly = true)
    public List<JobPostResponse> listSaved(String username) {
        requireSeeker(username, "view saved jobs");
        return savedJobRepo.findAllBySeekerUsernameOrderBySavedAtDesc(username).stream()
                .map(saved -> mapper.toResponse(saved.getJob())).toList();
    }

    private Seeker requireSeeker(String username, String action) {
        return seekerRepo.findByUsername(username).orElseThrow(() -> {
            log.warn("Denied: user '{}' tried to {} without the SEEKER role", username, action);
            return new LicenseValidationException("Only job seekers can " + action + ".");
        });
    }

    private JobPost findJob(Integer jobId) {
        return jobRepo.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("JobPost", jobId.toString()));
    }
}
