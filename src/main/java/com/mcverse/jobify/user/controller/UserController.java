package com.mcverse.jobify.user.controller;

import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.user.dto.*;
import com.mcverse.jobify.user.service.CompanyLogoService;
import com.mcverse.jobify.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Users", description = "Seeker and Employer profile management — requires Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private CompanyLogoService companyLogoService;

    // ── Seeker endpoints ──────────────────────────────────────────────────────

    @Operation(summary = "Get own seeker profile",
            description = "Returns the seeker profile of the currently authenticated user. Only accessible if the token belongs to a SEEKER account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Seeker profile",
                    content = @Content(schema = @Schema(implementation = SeekerResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Seeker profile not found for this username"),
    })
    @GetMapping("/seekers/me")
    public SeekerResponse getSeekerProfile(@AuthenticationPrincipal UserDetails principal) {
        return userService.getSeekerByUsername(principal.getUsername());
    }

    @Operation(summary = "Get seeker by ID",
            description = "Returns any seeker profile by its UUID. Accessible by all authenticated users.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Seeker profile",
                    content = @Content(schema = @Schema(implementation = SeekerResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Seeker not found"),
    })
    @GetMapping("/seekers/{id}")
    public SeekerResponse getSeekerById(
            @Parameter(description = "Seeker UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String id) {
        return userService.getSeekerById(id);
    }

    @Operation(summary = "Update own seeker profile",
            description = "Updates the first and last name of the authenticated seeker's profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated seeker profile",
                    content = @Content(schema = @Schema(implementation = SeekerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error — name or lastName is blank"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Seeker profile not found"),
    })
    @PutMapping("/seekers/me")
    public SeekerResponse updateSeekerProfile(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateSeeker(principal.getUsername(), request);
    }

    @Operation(summary = "Upload or replace the authenticated seeker's resume",
            description = "Accepts a PDF, DOC, or DOCX file up to 5MB. Stored under " +
                    "`<upload.dir>/<username>_resume/` — a new upload replaces whatever was there before.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resume stored; returns the updated seeker profile",
                    content = @Content(schema = @Schema(implementation = SeekerResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Seeker profile not found"),
            @ApiResponse(responseCode = "422", description = "Unsupported file type or file too large"),
    })
    @PostMapping(value = "/seekers/me/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SeekerResponse uploadResume(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam("file") MultipartFile file) {
        return userService.uploadResume(principal.getUsername(), file);
    }

    @Operation(summary = "Download the authenticated seeker's resume")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The resume file"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "No resume uploaded yet"),
    })
    @GetMapping("/seekers/me/resume")
    public ResponseEntity<Resource> downloadResume(@AuthenticationPrincipal UserDetails principal) {
        SeekerResponse seeker = userService.getSeekerByUsername(principal.getUsername());
        if (seeker.cv() == null) {
            throw new ResourceNotFoundException("Resume", principal.getUsername());
        }
        Resource resource = userService.loadResumeFile(principal.getUsername());
        String filename = seeker.cv().originalFileName().replace("\"", "");
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(seeker.cv().fileType());
        } catch (Exception e) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType)
                .body(resource);
    }

    @Operation(summary = "Delete the authenticated seeker's resume")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Resume deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "No resume uploaded yet"),
    })
    @DeleteMapping("/seekers/me/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResume(@AuthenticationPrincipal UserDetails principal) {
        userService.deleteResume(principal.getUsername());
    }

    // ── Employer endpoints ────────────────────────────────────────────────────

    @Operation(summary = "Get own employer profile",
            description = "Returns the employer profile of the currently authenticated user. Only accessible if the token belongs to an EMPLOYER account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employer profile (company may be null if not yet created)",
                    content = @Content(schema = @Schema(implementation = EmployerResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Employer profile not found"),
    })
    @GetMapping("/employers/me")
    public EmployerResponse getEmployerProfile(@AuthenticationPrincipal UserDetails principal) {
        return userService.getEmployerByUsername(principal.getUsername());
    }

    @Operation(summary = "Get employer by ID",
            description = "Returns any employer profile by its UUID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employer profile",
                    content = @Content(schema = @Schema(implementation = EmployerResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Employer not found"),
    })
    @GetMapping("/employers/{id}")
    public EmployerResponse getEmployerById(
            @Parameter(description = "Employer UUID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @PathVariable String id) {
        return userService.getEmployerById(id);
    }

    @Operation(summary = "Update own employer profile",
            description = "Updates the first and last name of the authenticated employer's profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated employer profile",
                    content = @Content(schema = @Schema(implementation = EmployerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Employer profile not found"),
    })
    @PutMapping("/employers/me")
    public EmployerResponse updateEmployerProfile(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateEmployer(principal.getUsername(), request);
    }

    // ── Company endpoints ─────────────────────────────────────────────────────

    @Operation(summary = "Create a company for the authenticated employer",
            description = "An employer can only have one company. Returns 422 if a company already exists — use the update endpoint instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Company created",
                    content = @Content(schema = @Schema(implementation = CompanyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Employer profile not found"),
            @ApiResponse(responseCode = "422", description = "Employer already has a company"),
    })
    @PostMapping("/companies")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CompanyRequest request) {
        return userService.createCompany(principal.getUsername(), request);
    }

    @Operation(summary = "Update the authenticated employer's company",
            description = "Only the employer who owns the company can update it. Returns 422 if the ID does not match the caller's company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company updated",
                    content = @Content(schema = @Schema(implementation = CompanyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Employer profile not found"),
            @ApiResponse(responseCode = "422", description = "Caller does not own this company"),
    })
    @PutMapping("/companies/{id}")
    public CompanyResponse updateCompany(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Company UUID", example = "7f3b2c4a-1234-5678-abcd-ef0123456789")
            @PathVariable String id,
            @Valid @RequestBody CompanyRequest request) {
        return userService.updateCompany(principal.getUsername(), id, request);
    }

    @Operation(summary = "Upload or replace the company logo",
            description = "Only the employer who owns the company. Multipart field `file`: PNG, JPEG or WebP, at most "
                    + "1 MB (the type is read from the file itself). The response carries the new `logoUrl`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logo stored",
                    content = @Content(schema = @Schema(implementation = CompanyResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Not the owner of this company"),
            @ApiResponse(responseCode = "404", description = "Company not found"),
            @ApiResponse(responseCode = "422", description = "Not a PNG, JPEG or WebP image, or larger than 1 MB"),
    })
    @PostMapping(value = "/companies/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompanyResponse uploadLogo(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Company UUID") @PathVariable String id,
            @RequestParam("file") MultipartFile file) {
        return companyLogoService.upload(principal.getUsername(), id, file);
    }

    @Operation(summary = "Remove the company logo", description = "Only the employer who owns the company.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logo removed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Not the owner of this company"),
            @ApiResponse(responseCode = "404", description = "Company not found, or it has no logo"),
    })
    @DeleteMapping("/companies/{id}/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLogo(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Company UUID") @PathVariable String id) {
        companyLogoService.remove(principal.getUsername(), id);
    }

    // ── Seeker education endpoints ──────────────────────────────────────────────

    @Operation(summary = "List the authenticated seeker's education entries")
    @GetMapping("/seekers/me/educations")
    public List<EducationResponse> listEducation(@AuthenticationPrincipal UserDetails principal) {
        return userService.listEducation(principal.getUsername());
    }

    @Operation(summary = "Add an education entry to the authenticated seeker's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Education entry created",
                    content = @Content(schema = @Schema(implementation = EducationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
    })
    @PostMapping("/seekers/me/educations")
    @ResponseStatus(HttpStatus.CREATED)
    public EducationResponse addEducation(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody EducationRequest request) {
        return userService.addEducation(principal.getUsername(), request);
    }

    @Operation(summary = "Update an education entry on the authenticated seeker's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Education entry updated"),
            @ApiResponse(responseCode = "404", description = "Education entry not found for this seeker"),
    })
    @PutMapping("/seekers/me/educations/{id}")
    public EducationResponse updateEducation(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable String id,
            @Valid @RequestBody EducationRequest request) {
        return userService.updateEducation(principal.getUsername(), id, request);
    }

    @Operation(summary = "Delete an education entry from the authenticated seeker's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Education entry deleted"),
            @ApiResponse(responseCode = "404", description = "Education entry not found for this seeker"),
    })
    @DeleteMapping("/seekers/me/educations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEducation(@AuthenticationPrincipal UserDetails principal, @PathVariable String id) {
        userService.deleteEducation(principal.getUsername(), id);
    }

    // ── Seeker certification endpoints ──────────────────────────────────────────

    @Operation(summary = "List the authenticated seeker's certifications")
    @GetMapping("/seekers/me/certifications")
    public List<CertificationResponse> listCertifications(@AuthenticationPrincipal UserDetails principal) {
        return userService.listCertifications(principal.getUsername());
    }

    @Operation(summary = "Add a certification to the authenticated seeker's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Certification created",
                    content = @Content(schema = @Schema(implementation = CertificationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
    })
    @PostMapping("/seekers/me/certifications")
    @ResponseStatus(HttpStatus.CREATED)
    public CertificationResponse addCertification(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CertificationRequest request) {
        return userService.addCertification(principal.getUsername(), request);
    }

    @Operation(summary = "Update a certification on the authenticated seeker's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Certification updated"),
            @ApiResponse(responseCode = "404", description = "Certification not found for this seeker"),
    })
    @PutMapping("/seekers/me/certifications/{id}")
    public CertificationResponse updateCertification(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable String id,
            @Valid @RequestBody CertificationRequest request) {
        return userService.updateCertification(principal.getUsername(), id, request);
    }

    @Operation(summary = "Delete a certification from the authenticated seeker's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Certification deleted"),
            @ApiResponse(responseCode = "404", description = "Certification not found for this seeker"),
    })
    @DeleteMapping("/seekers/me/certifications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCertification(@AuthenticationPrincipal UserDetails principal, @PathVariable String id) {
        userService.deleteCertification(principal.getUsername(), id);
    }

    // ── Seeker professional experience endpoints ────────────────────────────────

    @Operation(summary = "List the authenticated seeker's professional experience entries")
    @GetMapping("/seekers/me/experiences")
    public List<ProfessionalExperienceResponse> listExperiences(@AuthenticationPrincipal UserDetails principal) {
        return userService.listExperiences(principal.getUsername());
    }

    @Operation(summary = "Add a professional experience entry to the authenticated seeker's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Experience entry created",
                    content = @Content(schema = @Schema(implementation = ProfessionalExperienceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
    })
    @PostMapping("/seekers/me/experiences")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfessionalExperienceResponse addExperience(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ProfessionalExperienceRequest request) {
        return userService.addExperience(principal.getUsername(), request);
    }

    @Operation(summary = "Update a professional experience entry on the authenticated seeker's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Experience entry updated"),
            @ApiResponse(responseCode = "404", description = "Experience entry not found for this seeker"),
    })
    @PutMapping("/seekers/me/experiences/{id}")
    public ProfessionalExperienceResponse updateExperience(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable String id,
            @Valid @RequestBody ProfessionalExperienceRequest request) {
        return userService.updateExperience(principal.getUsername(), id, request);
    }

    @Operation(summary = "Delete a professional experience entry from the authenticated seeker's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Experience entry deleted"),
            @ApiResponse(responseCode = "404", description = "Experience entry not found for this seeker"),
    })
    @DeleteMapping("/seekers/me/experiences/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExperience(@AuthenticationPrincipal UserDetails principal, @PathVariable String id) {
        userService.deleteExperience(principal.getUsername(), id);
    }

    // ── Seeker skill endpoints ───────────────────────────────────────────────────

    @Operation(summary = "List the authenticated seeker's skills")
    @GetMapping("/seekers/me/skills")
    public List<SeekerSkillResponse> listSkills(@AuthenticationPrincipal UserDetails principal) {
        return userService.listSkills(principal.getUsername());
    }

    @Operation(summary = "Add a skill to the authenticated seeker's profile",
            description = "Matches `skillName` case-insensitively against the shared skill catalog, " +
                    "creating a new catalog entry if none exists.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Skill added",
                    content = @Content(schema = @Schema(implementation = SeekerSkillResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "422", description = "Seeker already has this skill"),
    })
    @PostMapping("/seekers/me/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public SeekerSkillResponse addSkill(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody SeekerSkillRequest request) {
        return userService.addSkill(principal.getUsername(), request);
    }

    @Operation(summary = "Update a skill's proficiency on the authenticated seeker's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Skill updated"),
            @ApiResponse(responseCode = "404", description = "Skill not found for this seeker"),
    })
    @PutMapping("/seekers/me/skills/{id}")
    public SeekerSkillResponse updateSkill(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable String id,
            @Valid @RequestBody SeekerSkillRequest request) {
        return userService.updateSkill(principal.getUsername(), id, request);
    }

    @Operation(summary = "Remove a skill from the authenticated seeker's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Skill removed"),
            @ApiResponse(responseCode = "404", description = "Skill not found for this seeker"),
    })
    @DeleteMapping("/seekers/me/skills/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSkill(@AuthenticationPrincipal UserDetails principal, @PathVariable String id) {
        userService.deleteSkill(principal.getUsername(), id);
    }
}
