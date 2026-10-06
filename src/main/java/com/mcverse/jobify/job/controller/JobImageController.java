package com.mcverse.jobify.job.controller;

import com.mcverse.jobify.common.storage.ImageResponses;
import com.mcverse.jobify.job.dto.JobPostResponse;
import com.mcverse.jobify.job.service.JobImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@Tag(name = "Job images", description = "Pictures on a job posting (at most 6)")
@RestController
@RequestMapping("/jobs/{jobId}/images")
public class JobImageController {

    private final JobImageService images;

    public JobImageController(JobImageService images) {
        this.images = images;
    }

    @Operation(summary = "Add an image to a job",
            description = "Only the employer who posted the job. Multipart field `file`: PNG, JPEG or WebP, at most "
                    + "2 MB; a job can hold 6. Returns the updated job, whose `images` lists the URLs.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Image stored",
                    content = @Content(schema = @Schema(implementation = JobPostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Not the owner of this job"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "422", description = "Not an image, too large, or the job already has 6"),
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public JobPostResponse add(@AuthenticationPrincipal UserDetails principal,
                               @PathVariable Integer jobId, @RequestParam("file") MultipartFile file) {
        return images.add(jobId, principal.getUsername(), file);
    }

    @Operation(summary = "Remove an image from a job", description = "Only the employer who posted the job. "
            + "Returns the updated job.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image removed",
                    content = @Content(schema = @Schema(implementation = JobPostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Not the owner of this job"),
            @ApiResponse(responseCode = "404", description = "Job or image not found"),
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{imageId}")
    public JobPostResponse remove(@AuthenticationPrincipal UserDetails principal, @PathVariable Integer jobId,
                                  @Parameter(description = "Image id, the last part of its URL") @PathVariable String imageId) {
        return images.remove(jobId, imageId, principal.getUsername());
    }

    @Operation(summary = "Download a job image", description = "Public. The addresses in `images` on the job point "
            + "here. Cacheable for a year: an image never changes, it can only be removed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The image"),
            @ApiResponse(responseCode = "404", description = "Job or image not found"),
    })
    @SecurityRequirements
    @GetMapping("/{imageId}")
    public ResponseEntity<Resource> get(@PathVariable Integer jobId, @PathVariable String imageId) {
        var image = images.read(jobId, imageId);
        return ImageResponses.of(image.file(), image.contentType(), Duration.ofDays(365));
    }
}
