package com.mcverse.jobify.job.service;

import com.mcverse.jobify.common.exception.BusinessRuleException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.common.storage.AfterCommit;
import com.mcverse.jobify.common.storage.FileStorageService;
import com.mcverse.jobify.job.dto.JobLimits;
import com.mcverse.jobify.job.dto.JobPostResponse;
import com.mcverse.jobify.job.model.JobImage;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.repository.JobRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Pictures on a job. Only the employer who posted the job may add or remove them; reading is public. */
@Service
public class JobImageService {

    private final JobService jobs;
    private final JobRepo jobRepo;
    private final FileStorageService storage;
    private final JobResponseMapper mapper;

    public JobImageService(JobService jobs, JobRepo jobRepo, FileStorageService storage, JobResponseMapper mapper) {
        this.jobs = jobs;
        this.jobRepo = jobRepo;
        this.storage = storage;
        this.mapper = mapper;
    }

    @Transactional
    public JobPostResponse add(Integer jobId, String username, MultipartFile file) {
        JobPost job = jobs.findOwnedJob(jobId, username);
        if (job.getImages().size() >= JobLimits.IMAGES_MAX) {
            throw new BusinessRuleException("A job can have at most " + JobLimits.IMAGES_MAX
                    + " images. Remove one first.");
        }
        String fileId = UUID.randomUUID().toString();
        var stored = storage.storeImage("job-images/" + jobId, fileId, file, JobLimits.IMAGE_MAX_BYTES, "Image");
        int position = job.getImages().stream().mapToInt(JobImage::getPosition).max().orElse(-1) + 1;
        job.getImages().add(new JobImage(job, stored.relativePath(), stored.contentType(), position));
        return mapper.toResponse(jobRepo.save(job));
    }

    @Transactional
    public JobPostResponse remove(Integer jobId, String imageId, String username) {
        JobPost job = jobs.findOwnedJob(jobId, username);
        JobImage image = job.getImages().stream().filter(i -> i.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image", imageId));
        job.getImages().remove(image);
        String path = image.getFilePath();
        AfterCommit.run(() -> storage.delete(path));
        return mapper.toResponse(jobRepo.save(job));
    }

    @Transactional(readOnly = true)
    public Image read(Integer jobId, String imageId) {
        JobPost job = jobRepo.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("JobPost", jobId.toString()));
        JobImage image = job.getImages().stream().filter(i -> i.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image", imageId));
        return new Image(storage.resolveForRead(image.getFilePath()), image.getContentType());
    }

    /** Called before an employer's jobs are deleted with their account: the rows go with the jobs, the files here. */
    @Transactional(readOnly = true)
    public void deleteFilesOfEmployer(String username) {
        List<String> paths = jobRepo.findAllByEmployerUsername(username).stream()
                .flatMap(j -> j.getImages().stream()).map(JobImage::getFilePath).toList();
        AfterCommit.run(() -> paths.forEach(storage::delete));
    }

    public record Image(Path file, String contentType) {}
}
