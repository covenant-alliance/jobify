package com.mcverse.jobify.user.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.common.storage.AfterCommit;
import com.mcverse.jobify.common.storage.FileStorageService;
import com.mcverse.jobify.user.dto.CompanyResponse;
import com.mcverse.jobify.user.model.Company;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.repository.CompanyRepository;
import com.mcverse.jobify.user.repository.EmployerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/** Upload, removal and reading of a company's logo. Only the employer whose company it is may change it. */
@Service
public class CompanyLogoService {

    public static final long MAX_BYTES = 1024L * 1024;

    private final CompanyRepository companies;
    private final EmployerRepository employers;
    private final FileStorageService storage;
    private final AuditLog auditLog;
    private final CompanyAccess access;

    public CompanyLogoService(CompanyRepository companies, EmployerRepository employers, FileStorageService storage,
                              AuditLog auditLog, CompanyAccess access) {
        this.access = access;
        this.companies = companies;
        this.employers = employers;
        this.storage = storage;
        this.auditLog = auditLog;
    }

    @Transactional
    public CompanyResponse upload(String username, String companyId, MultipartFile file) {
        Company company = requireOwnCompany(username, companyId);
        long version = System.currentTimeMillis();
        var stored = storage.storeImage("company-logos/" + company.getId(), Long.toString(version), file, MAX_BYTES,
                "Logo");
        String oldPath = company.getLogoPath();
        company.setLogo(stored.relativePath(), stored.contentType(), version);
        if (oldPath != null) AfterCommit.run(() -> storage.delete(oldPath));
        Company saved = companies.save(company);
        return new CompanyResponse(saved.getId(), saved.getName(), CompanyLogos.urlOf(saved));
    }

    @Transactional
    public void remove(String username, String companyId) {
        Company company = requireOwnCompany(username, companyId);
        String path = company.getLogoPath();
        if (path == null) {
            throw new ResourceNotFoundException("Logo", companyId);
        }
        company.clearLogo();
        companies.save(company);
        AfterCommit.run(() -> storage.delete(path));
    }

    /** The logo file and its type, for the public download. 404 when the company has none. */
    @Transactional(readOnly = true)
    public Logo read(String companyId) {
        Company company = companies.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));
        if (company.getLogoPath() == null) {
            throw new ResourceNotFoundException("Logo", companyId);
        }
        return new Logo(storage.resolveForRead(company.getLogoPath()), company.getLogoContentType());
    }

    /** Called when an employer account is removed: the company row stays, but its picture must not. */
    @Transactional
    public void dropLogoOfEmployer(String username) {
        employers.findByUsername(username).map(Employer::getCompany).ifPresent(company -> {
            String path = company.getLogoPath();
            if (path != null) {
                company.clearLogo();
                companies.save(company);
                AfterCommit.run(() -> storage.delete(path));
            }
        });
    }

    private Company requireOwnCompany(String username, String companyId) {
        Company company = companies.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));
        Employer employer = employers.findByUsername(username).orElse(null);
        if (employer == null || employer.getCompany() == null || !employer.getCompany().getId().equals(companyId)) {
            auditLog.event(username, "ACCESS_DENIED", "action='change company logo' company=" + companyId);
            throw new LicenseValidationException("You can only change the logo of your own company.");
        }
        access.requireOwner(username, employer);
        return company;
    }

    public record Logo(Path file, String contentType) {}
}
