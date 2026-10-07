package com.mcverse.jobify.user.service;

import com.mcverse.jobify.auth.model.Role;
import com.mcverse.jobify.common.exception.BusinessRuleException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.common.storage.FileStorageService;
import com.mcverse.jobify.user.dto.*;
import com.mcverse.jobify.user.model.Certification;
import com.mcverse.jobify.user.model.Company;
import com.mcverse.jobify.user.model.CompanyRole;
import com.mcverse.jobify.user.model.Cv;
import com.mcverse.jobify.user.model.Education;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.model.ProfessionalExperience;
import com.mcverse.jobify.user.model.Seeker;
import com.mcverse.jobify.user.model.SeekerSkill;
import com.mcverse.jobify.user.model.Skill;
import com.mcverse.jobify.user.repository.CertificationRepository;
import com.mcverse.jobify.user.repository.CompanyRepository;
import com.mcverse.jobify.user.repository.EducationRepository;
import com.mcverse.jobify.user.repository.EmployerRepository;
import com.mcverse.jobify.user.repository.ProfessionalExperienceRepository;
import com.mcverse.jobify.user.repository.SeekerRepository;
import com.mcverse.jobify.user.repository.SeekerSkillRepository;
import com.mcverse.jobify.user.repository.SkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    @Autowired private SeekerRepository seekerRepo;
    @Autowired private EmployerRepository employerRepo;
    @Autowired private CompanyAccess access;
    @Autowired private CompanyRepository companyRepo;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private EducationRepository educationRepo;
    @Autowired private CertificationRepository certificationRepo;
    @Autowired private ProfessionalExperienceRepository experienceRepo;
    @Autowired private SeekerSkillRepository seekerSkillRepo;
    @Autowired private SkillRepository skillRepo;

    @Transactional
    public void createProfile(String username, String firstName, String lastName, Role role) {
        if (role == Role.SEEKER) {
            seekerRepo.save(new Seeker(firstName, lastName, username, false));
        } else if (role == Role.EMPLOYER) {
            employerRepo.save(new Employer(firstName, lastName, username));
        }
        // ADMIN has no domain profile
    }

    @Transactional(readOnly = true)
    public SeekerResponse getSeekerByUsername(String username) {
        Seeker seeker = seekerRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seeker", username));
        return toSeekerResponse(seeker);
    }

    @Transactional(readOnly = true)
    public SeekerResponse getSeekerById(String id) {
        Seeker seeker = seekerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seeker", id));
        return toSeekerResponse(seeker);
    }

    public EmployerResponse getEmployerByUsername(String username) {
        Employer employer = employerRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Employer", username));
        return toEmployerResponse(employer);
    }

    public EmployerResponse getEmployerById(String id) {
        Employer employer = employerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employer", id));
        return toEmployerResponse(employer);
    }

    @Transactional
    public SeekerResponse updateSeeker(String username, UpdateProfileRequest request) {
        Seeker seeker = seekerRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seeker", username));
        seeker.setName(request.name());
        seeker.setLastName(request.lastName());
        return toSeekerResponse(seekerRepo.save(seeker));
    }

    @Transactional
    public EmployerResponse updateEmployer(String username, UpdateProfileRequest request) {
        Employer employer = employerRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Employer", username));
        employer.setName(request.name());
        employer.setLastName(request.lastName());
        return toEmployerResponse(employerRepo.save(employer));
    }

    @Transactional
    public SeekerResponse uploadResume(String username, MultipartFile file) {
        Seeker seeker = seekerRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seeker", username));

        if (seeker.getCv() != null) {
            fileStorageService.delete(seeker.getCv().getFileUrl());
        }

        FileStorageService.StoredFile stored = fileStorageService.storeResume(username, file);
        Cv cv = seeker.getCv();
        if (cv == null) {
            cv = new Cv(stored.relativePath(), stored.contentType(), LocalDateTime.now());
            seeker.setCv(cv);
        } else {
            cv.setFileUrl(stored.relativePath());
            cv.setFileType(stored.contentType());
            cv.setFileDate(LocalDateTime.now());
        }
        cv.setOriginalFileName(stored.originalFileName());

        return toSeekerResponse(seekerRepo.save(seeker));
    }

    @Transactional
    public void deleteResume(String username) {
        Seeker seeker = seekerRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seeker", username));
        if (seeker.getCv() == null) {
            throw new ResourceNotFoundException("Resume", username);
        }
        fileStorageService.delete(seeker.getCv().getFileUrl());
        seeker.setCv(null);
        seekerRepo.save(seeker);
    }

    public Resource loadResumeFile(String username) {
        Seeker seeker = seekerRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seeker", username));
        if (seeker.getCv() == null) {
            throw new ResourceNotFoundException("Resume", username);
        }
        return new FileSystemResource(fileStorageService.resolveForRead(seeker.getCv().getFileUrl()));
    }

    @Transactional
    public CompanyResponse createCompany(String username, CompanyRequest request) {
        Employer employer = employerRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Employer", username));
        if (employer.getCompany() != null) {
            throw new BusinessRuleException("Employer already has a company. Update it instead.");
        }
        Company company = companyRepo.save(new Company(request.name()));
        employer.setCompany(company);
        employer.setCompanyRole(CompanyRole.OWNER);
        employerRepo.save(employer);
        return toCompanyResponse(company);
    }

    @Transactional
    public CompanyResponse updateCompany(String username, String companyId, CompanyRequest request) {
        Employer employer = employerRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Employer", username));
        Company company = employer.getCompany();
        if (company == null || !company.getId().equals(companyId)) {
            throw new BusinessRuleException("You do not own this company.");
        }
        access.requireOwner(username, employer);
        company.setName(request.name());
        return toCompanyResponse(companyRepo.save(company));
    }

    // --- education ---

    @Transactional(readOnly = true)
    public List<EducationResponse> listEducation(String username) {
        Seeker seeker = resolveSeeker(username);
        return educationRepo.findBySeekerId(seeker.getId()).stream().map(this::toEducationResponse).toList();
    }

    @Transactional
    public EducationResponse addEducation(String username, EducationRequest request) {
        Seeker seeker = resolveSeeker(username);
        validateDateRange(request.startDate(), request.endDate());
        Education education = new Education(seeker, request.institution(), request.degree(), request.fieldOfStudy(),
                request.startDate(), request.endDate(), request.grade());
        return toEducationResponse(educationRepo.save(education));
    }

    @Transactional
    public EducationResponse updateEducation(String username, String id, EducationRequest request) {
        Seeker seeker = resolveSeeker(username);
        Education education = educationRepo.findByIdAndSeekerId(id, seeker.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Education", id));
        validateDateRange(request.startDate(), request.endDate());
        education.setInstitution(request.institution());
        education.setDegree(request.degree());
        education.setFieldOfStudy(request.fieldOfStudy());
        education.setStartDate(request.startDate());
        education.setEndDate(request.endDate());
        education.setGrade(request.grade());
        return toEducationResponse(educationRepo.save(education));
    }

    @Transactional
    public void deleteEducation(String username, String id) {
        Seeker seeker = resolveSeeker(username);
        Education education = educationRepo.findByIdAndSeekerId(id, seeker.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Education", id));
        educationRepo.delete(education);
    }

    // --- certifications ---

    @Transactional(readOnly = true)
    public List<CertificationResponse> listCertifications(String username) {
        Seeker seeker = resolveSeeker(username);
        return certificationRepo.findBySeekerId(seeker.getId()).stream().map(this::toCertificationResponse).toList();
    }

    @Transactional
    public CertificationResponse addCertification(String username, CertificationRequest request) {
        Seeker seeker = resolveSeeker(username);
        Certification certification = new Certification(seeker, request.name(), request.issuingOrganization(),
                request.issueDate(), request.expirationDate(), request.credentialId(), request.credentialUrl());
        return toCertificationResponse(certificationRepo.save(certification));
    }

    @Transactional
    public CertificationResponse updateCertification(String username, String id, CertificationRequest request) {
        Seeker seeker = resolveSeeker(username);
        Certification certification = certificationRepo.findByIdAndSeekerId(id, seeker.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Certification", id));
        certification.setName(request.name());
        certification.setIssuingOrganization(request.issuingOrganization());
        certification.setIssueDate(request.issueDate());
        certification.setExpirationDate(request.expirationDate());
        certification.setCredentialId(request.credentialId());
        certification.setCredentialUrl(request.credentialUrl());
        return toCertificationResponse(certificationRepo.save(certification));
    }

    @Transactional
    public void deleteCertification(String username, String id) {
        Seeker seeker = resolveSeeker(username);
        Certification certification = certificationRepo.findByIdAndSeekerId(id, seeker.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Certification", id));
        certificationRepo.delete(certification);
    }

    // --- professional experience ---

    @Transactional(readOnly = true)
    public List<ProfessionalExperienceResponse> listExperiences(String username) {
        Seeker seeker = resolveSeeker(username);
        return experienceRepo.findBySeekerId(seeker.getId()).stream().map(this::toExperienceResponse).toList();
    }

    @Transactional
    public ProfessionalExperienceResponse addExperience(String username, ProfessionalExperienceRequest request) {
        Seeker seeker = resolveSeeker(username);
        validateDateRange(request.startDate(), request.endDate());
        ProfessionalExperience experience = new ProfessionalExperience(seeker, request.jobTitle(), request.companyName(),
                request.location(), request.employmentType(), request.startDate(), request.endDate(), request.description());
        return toExperienceResponse(experienceRepo.save(experience));
    }

    @Transactional
    public ProfessionalExperienceResponse updateExperience(String username, String id, ProfessionalExperienceRequest request) {
        Seeker seeker = resolveSeeker(username);
        ProfessionalExperience experience = experienceRepo.findByIdAndSeekerId(id, seeker.getId())
                .orElseThrow(() -> new ResourceNotFoundException("ProfessionalExperience", id));
        validateDateRange(request.startDate(), request.endDate());
        experience.setJobTitle(request.jobTitle());
        experience.setCompanyName(request.companyName());
        experience.setLocation(request.location());
        experience.setEmploymentType(request.employmentType());
        experience.setStartDate(request.startDate());
        experience.setEndDate(request.endDate());
        experience.setDescription(request.description());
        return toExperienceResponse(experienceRepo.save(experience));
    }

    @Transactional
    public void deleteExperience(String username, String id) {
        Seeker seeker = resolveSeeker(username);
        ProfessionalExperience experience = experienceRepo.findByIdAndSeekerId(id, seeker.getId())
                .orElseThrow(() -> new ResourceNotFoundException("ProfessionalExperience", id));
        experienceRepo.delete(experience);
    }

    // --- skills ---

    @Transactional(readOnly = true)
    public List<SeekerSkillResponse> listSkills(String username) {
        Seeker seeker = resolveSeeker(username);
        return seekerSkillRepo.findBySeekerId(seeker.getId()).stream().map(this::toSeekerSkillResponse).toList();
    }

    @Transactional
    public SeekerSkillResponse addSkill(String username, SeekerSkillRequest request) {
        Seeker seeker = resolveSeeker(username);
        Skill skill = skillRepo.findByNameIgnoreCase(request.skillName())
                .orElseGet(() -> skillRepo.save(new Skill(request.skillName(), request.category())));
        seekerSkillRepo.findBySeekerIdAndSkillId(seeker.getId(), skill.getId())
                .ifPresent(existing -> { throw new BusinessRuleException("You already have this skill on your profile. Update it instead."); });
        SeekerSkill seekerSkill = new SeekerSkill(seeker, skill, request.proficiencyLevel(), request.yearsOfExperience());
        return toSeekerSkillResponse(seekerSkillRepo.save(seekerSkill));
    }

    @Transactional
    public SeekerSkillResponse updateSkill(String username, String id, SeekerSkillRequest request) {
        Seeker seeker = resolveSeeker(username);
        SeekerSkill seekerSkill = seekerSkillRepo.findByIdAndSeekerId(id, seeker.getId())
                .orElseThrow(() -> new ResourceNotFoundException("SeekerSkill", id));
        seekerSkill.setProficiencyLevel(request.proficiencyLevel());
        seekerSkill.setYearsOfExperience(request.yearsOfExperience());
        return toSeekerSkillResponse(seekerSkillRepo.save(seekerSkill));
    }

    @Transactional
    public void deleteSkill(String username, String id) {
        Seeker seeker = resolveSeeker(username);
        SeekerSkill seekerSkill = seekerSkillRepo.findByIdAndSeekerId(id, seeker.getId())
                .orElseThrow(() -> new ResourceNotFoundException("SeekerSkill", id));
        seekerSkillRepo.delete(seekerSkill);
    }

    // --- helpers ---

    private Seeker resolveSeeker(String username) {
        return seekerRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Seeker", username));
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessRuleException("End date cannot be before start date.");
        }
    }

    // --- mappers ---

    private SeekerResponse toSeekerResponse(Seeker s) {
        CvResponse cv = s.getCv() != null
                ? new CvResponse(s.getCv().getOriginalFileName(), s.getCv().getFileType(), s.getCv().getFileDate())
                : null;
        return new SeekerResponse(s.getId(), s.getUsername(), s.getName(), s.getLastName(),
                s.getCreationDate(), s.isIndependent(), cv,
                s.getEducations().stream().map(this::toEducationResponse).toList(),
                s.getCertifications().stream().map(this::toCertificationResponse).toList(),
                s.getProfessionalExperiences().stream().map(this::toExperienceResponse).toList(),
                s.getSeekerSkills().stream().map(this::toSeekerSkillResponse).toList());
    }

    private EmployerResponse toEmployerResponse(Employer e) {
        CompanyResponse company = e.getCompany() != null ? toCompanyResponse(e.getCompany()) : null;
        return new EmployerResponse(e.getId(), e.getUsername(), e.getName(), e.getLastName(),
                e.getCreationDate(), company, e.getCompanyRole());
    }

    private CompanyResponse toCompanyResponse(Company c) {
        return new CompanyResponse(c.getId(), c.getName(), CompanyLogos.urlOf(c));
    }

    private EducationResponse toEducationResponse(Education e) {
        return new EducationResponse(e.getId(), e.getInstitution(), e.getDegree(), e.getFieldOfStudy(),
                e.getStartDate(), e.getEndDate(), e.getGrade());
    }

    private CertificationResponse toCertificationResponse(Certification c) {
        return new CertificationResponse(c.getId(), c.getName(), c.getIssuingOrganization(), c.getIssueDate(),
                c.getExpirationDate(), c.getCredentialId(), c.getCredentialUrl());
    }

    private ProfessionalExperienceResponse toExperienceResponse(ProfessionalExperience p) {
        return new ProfessionalExperienceResponse(p.getId(), p.getJobTitle(), p.getCompanyName(), p.getLocation(),
                p.getEmploymentType(), p.getStartDate(), p.getEndDate(), p.getDescription());
    }

    private SeekerSkillResponse toSeekerSkillResponse(SeekerSkill s) {
        return new SeekerSkillResponse(s.getId(), s.getSkill().getName(), s.getSkill().getCategory(),
                s.getProficiencyLevel(), s.getYearsOfExperience());
    }
}
