package com.mcverse.jobify.user.service;

import com.mcverse.jobify.user.model.CompanyRole;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.repository.EmployerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Writes down OWNER for employers whose company was set before company roles existed. Harmless to repeat. */
@Component
@Order(140)
public class CompanyRoleBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanyRoleBackfill.class);

    private final EmployerRepository employers;
    private final TransactionTemplate tx;

    public CompanyRoleBackfill(EmployerRepository employers, TransactionTemplate tx) {
        this.employers = employers;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer filled = tx.execute(status -> {
            var legacy = employers.findAllByCompanyIsNotNullAndCompanyRoleIsNull();
            for (Employer e : legacy) {
                e.setCompanyRole(CompanyRole.OWNER);
            }
            employers.saveAll(legacy);
            return legacy.size();
        });
        if (filled != null && filled > 0) {
            log.info("Set the company role of {} existing employers to OWNER", filled);
        }
    }
}
