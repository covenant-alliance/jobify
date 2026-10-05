package com.mcverse.jobify.config;

import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.model.EmploymentType;
import com.mcverse.jobify.job.model.RateType;
import com.mcverse.jobify.model.JobPost;
import com.mcverse.jobify.model.WorkMode;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.repository.EmployerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Separated from DataInitializer so @Transactional is effective through the Spring proxy.
 * Employers must be managed entities when job posts that reference them are persisted.
 */
@Service
public class SeedService {

    @Autowired private EmployerRepository employerRepo;
    @Autowired private JobRepo jobRepo;

    @Transactional
    public void seedJobPosts() {
        Employer techcorp      = employerRepo.findByUsername("techcorp").orElseThrow();
        Employer startupxyz    = employerRepo.findByUsername("startupxyz").orElseThrow();
        Employer financegroup  = employerRepo.findByUsername("financegroup").orElseThrow();

        jobRepo.saveAll(List.of(
                // ── TechCorp ──────────────────────────────────────────────────────────
                post("Senior Frontend Developer",
                        "<p>Senior React and TypeScript developer needed to lead our web platform. " +
                        "5+ years of hands-on experience with React, Next.js, and REST APIs required.</p>",
                        75.00, techcorp, true, "San Francisco, CA", WorkMode.HYBRID, EmploymentType.FULL_TIME),

                post("Backend Engineer",
                        "<p>Senior Spring Boot developer for a high-throughput fintech platform. " +
                        "5+ years of Java experience, strong knowledge of JPA and microservices.</p>",
                        72.50, techcorp, true, "Remote — US", WorkMode.REMOTE, EmploymentType.FULL_TIME),

                post("Junior QA Engineer",
                        "<p>Entry-level QA engineer to join our quality team. " +
                        "0-2 years of testing experience. Training provided — great way to start your career.</p>",
                        35.00, techcorp, false, "San Francisco, CA", WorkMode.ONSITE, EmploymentType.FULL_TIME),  // position filled

                // ── StartupXYZ ───────────────────────────────────────────────────────
                post("Product Designer",
                        "<p>Mid-level UX/UI designer with 3+ years of experience in Figma, " +
                        "user research, and design systems. You will own the end-to-end design process.</p>",
                        55.00, startupxyz, true, "Austin, TX", WorkMode.HYBRID, EmploymentType.FULL_TIME),

                post("DevOps Engineer",
                        "<p>Senior DevOps engineer with 7+ years of experience managing cloud infrastructure. " +
                        "AWS-certified preferred. Expertise in Kubernetes, Terraform, and CI/CD pipelines.</p>",
                        85.00, startupxyz, true, "Remote — Global", WorkMode.REMOTE, EmploymentType.CONTRACT),

                post("Full-Stack Developer",
                        "<p>Mid-level full-stack developer with 2-5 years of experience in React and Node.js. " +
                        "You will ship features across our entire product stack.</p>",
                        60.00, startupxyz, true, "Austin, TX", WorkMode.ONSITE, EmploymentType.FULL_TIME),

                // ── Finance Group ─────────────────────────────────────────────────────
                post("Senior Data Scientist",
                        "<p>Senior data scientist with 5+ years of experience in Python, scikit-learn, and " +
                        "financial modelling. You will build predictive models for risk and revenue.</p>",
                        80.00, financegroup, true, "New York, NY", WorkMode.HYBRID, EmploymentType.FULL_TIME),

                post("Junior Business Analyst",
                        "<p>Entry-level business analyst. 1+ years of experience in data analysis or finance. " +
                        "You will support senior analysts with reporting and dashboard creation.</p>",
                        40.00, financegroup, false, "New York, NY", WorkMode.ONSITE, EmploymentType.FULL_TIME),  // position filled

                post("ML Engineer",
                        "<p>Senior machine learning engineer with 5+ years of experience. " +
                        "Deep learning and NLP expertise required. You will productionise ML models at scale.</p>",
                        90.00, financegroup, true, "Remote — US", WorkMode.REMOTE, EmploymentType.FREELANCE)
        ));
    }

    private static JobPost post(String title, String description, double rate,
                                Employer employer, boolean available, String location,
                                WorkMode workMode, EmploymentType employmentType) {
        JobPost p = new JobPost(title, description, rate, RateType.HOURLY);
        p.setEmployer(employer);
        p.setAvailable(available);
        p.setLocation(location);
        p.setWorkMode(workMode);
        p.setEmploymentType(employmentType);
        return p;
    }
}
