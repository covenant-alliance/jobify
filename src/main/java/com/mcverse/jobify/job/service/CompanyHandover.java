package com.mcverse.jobify.job.service;

import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.user.model.CompanyRole;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.repository.EmployerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** What happens to a company's jobs when one of its people leaves (their account is deleted). */
@Service
public class CompanyHandover {

    private static final Logger log = LoggerFactory.getLogger(CompanyHandover.class);

    private final EmployerRepository employers;
    private final JobRepo jobs;

    public CompanyHandover(EmployerRepository employers, JobRepo jobs) {
        this.employers = employers;
        this.jobs = jobs;
    }

    /**
     * Passes every job of {@code from} to {@code to}. A bulk update that detaches everything loaded so far, so call
     * it last in a transaction and read what you still need beforehand.
     */
    @Transactional
    public int passJobs(Employer from, Employer to) {
        return jobs.reassign(from, to);
    }

    /**
     * If the person belongs to a company that other people also belong to, passes their jobs to one of those
     * colleagues (an owner if there is one) and makes sure the company still has an owner. Returns true when it did;
     * false means they are on their own and their jobs go with them.
     */
    @Transactional
    public boolean handOverToColleague(String username) {
        Employer leaving = employers.findByUsername(username).orElse(null);
        if (leaving == null || leaving.getCompany() == null) {
            return false;
        }
        List<Employer> colleagues = employers
                .findAllByCompanyIdOrderByCreationDateAscIdAsc(leaving.getCompany().getId()).stream()
                .filter(e -> !e.getUsername().equals(username)).toList();
        if (colleagues.isEmpty()) {
            return false;
        }
        Employer successor = colleagues.stream().filter(e -> e.getCompanyRole() == CompanyRole.OWNER).findFirst()
                .orElseGet(() -> colleagues.stream().min(Comparator.comparing(Employer::getCreationDate)).orElseThrow());
        if (successor.getCompanyRole() != CompanyRole.OWNER) {
            successor.setCompanyRole(CompanyRole.OWNER); // a company is never left without an owner
            employers.save(successor);
        }
        String company = leaving.getCompany().getName(); // read before the bulk update detaches everything
        String successorName = successor.getUsername();
        int moved = jobs.reassign(leaving, successor);
        log.info("Handed {} jobs of '{}' to '{}' ({})", moved, username, successorName, company);
        return true;
    }
}
