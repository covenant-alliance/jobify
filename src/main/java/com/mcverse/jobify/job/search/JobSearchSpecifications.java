package com.mcverse.jobify.job.search;

import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.model.RateType;
import com.mcverse.jobify.job.search.JobSearchCriteria.SkillsMatch;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.model.Skill;
import com.mcverse.jobify.user.model.Company;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the database query for a {@link JobSearchCriteria} with the portable JPA Criteria API (no vendor SQL, so the
 * same code runs on H2, PostgreSQL and anything else Hibernate supports).
 *
 * <p>Rules worth knowing:
 * <ul>
 *   <li>Text terms are a case-insensitive "contains" with LIKE wildcards escaped, so {@code 50%} means exactly that.</li>
 *   <li>The rate filter and the rate sort compare on the <b>hourly equivalent</b> (monthly / 173.33, yearly / 2080),
 *       the same conversion as {@link RateType#toHourly}, rounded to cents by a half-cent tolerance on the bounds.
 *       {@code CONTRACT_TOTAL} cannot be compared, so it is excluded whenever a rate bound is set and sorts last when
 *       sorting by rate.</li>
 *   <li>Skills are tested with sub-queries, so a job never appears twice and paging counts are exact.</li>
 *   <li>Every order ends with the job id, so pages never overlap or skip.</li>
 * </ul>
 */
public final class JobSearchSpecifications {

    /** Half a cent: a bound of 34.62 accepts an hourly equivalent of 34.616, which rounds to 34.62. */
    private static final double HALF_CENT = 0.005;

    private JobSearchSpecifications() {}

    public static Specification<JobPost> matching(JobSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            availability(criteria, root, cb, where);
            if (criteria.q() != null) {
                where.add(text(criteria.q(), root, query, cb));
            }
            if (criteria.location() != null) {
                where.add(cb.like(cb.lower(root.get("location")), likePattern(criteria.location()), '\\'));
            }
            if (!criteria.locationIds().isEmpty()) {
                where.add(root.get("locationRef").get("id").in(criteria.locationIds()));
            }
            if (!criteria.workModes().isEmpty()) {
                where.add(root.get("workMode").in(criteria.workModes()));
            }
            if (!criteria.employmentTypes().isEmpty()) {
                where.add(root.get("employmentType").in(criteria.employmentTypes()));
            }
            if (criteria.hasRateFilter()) {
                rate(criteria, root, cb, where);
            }
            if (!criteria.skills().isEmpty()) {
                skills(criteria, root, query, cb, where);
            }
            // the count query used for paging must not carry an ORDER BY
            if (!Long.class.equals(query.getResultType())) {
                query.orderBy(order(criteria, root, cb));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static void availability(JobSearchCriteria criteria, Root<JobPost> root, CriteriaBuilder cb,
                                     List<Predicate> where) {
        switch (criteria.availability()) {
            case OPEN -> where.add(cb.isTrue(root.get("available")));
            case CLOSED -> where.add(cb.isFalse(root.get("available")));
            case ALL -> { }
        }
    }

    /** Title, company name, location, description words or a required skill's name contains the term. */
    private static Predicate text(String term, Root<JobPost> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        String pattern = likePattern(term);
        Join<JobPost, Employer> employer = root.join("employer", JoinType.LEFT);
        Join<Employer, Company> company = employer.join("company", JoinType.LEFT);
        return cb.or(
                cb.like(cb.lower(root.get("jobTitle")), pattern, '\\'),
                cb.like(cb.lower(company.get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("location")), pattern, '\\'),
                cb.like(cb.lower(root.<String>get("descriptionText").as(String.class)), pattern, '\\'),
                skillNameContains(pattern, root, query, cb));
    }

    private static Predicate skillNameContains(String pattern, Root<JobPost> root, CriteriaQuery<?> query,
                                               CriteriaBuilder cb) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<JobPost> job = sub.correlate(root);
        Join<JobPost, Skill> skill = job.join("requiredSkills");
        sub.select(cb.literal(1)).where(cb.like(cb.lower(skill.get("name")), pattern, '\\'));
        return cb.exists(sub);
    }

    private static void rate(JobSearchCriteria criteria, Root<JobPost> root, CriteriaBuilder cb,
                             List<Predicate> where) {
        where.add(cb.notEqual(root.get("rateType"), RateType.CONTRACT_TOTAL));
        Expression<Double> hourly = hourlyEquivalent(root, cb);
        if (criteria.minRate() != null) {
            where.add(cb.greaterThanOrEqualTo(hourly, criteria.minRate() - HALF_CENT));
        }
        if (criteria.maxRate() != null) {
            where.add(cb.lessThan(hourly, criteria.maxRate() + HALF_CENT));
        }
    }

    /** The pay as an amount per hour; for CONTRACT_TOTAL it is the raw amount, so callers must exclude it first. */
    private static Expression<Double> hourlyEquivalent(Root<JobPost> root, CriteriaBuilder cb) {
        Expression<Double> rate = root.get("rate");
        return cb.<Double>selectCase()
                .when(cb.equal(root.get("rateType"), RateType.MONTHLY),
                        cb.quot(rate, RateType.HOURS_PER_MONTH).as(Double.class))
                .when(cb.equal(root.get("rateType"), RateType.YEARLY),
                        cb.quot(rate, RateType.HOURS_PER_YEAR).as(Double.class))
                .otherwise(rate);
    }

    private static void skills(JobSearchCriteria criteria, Root<JobPost> root, CriteriaQuery<?> query,
                               CriteriaBuilder cb, List<Predicate> where) {
        List<Predicate> perSkill = new ArrayList<>();
        for (String name : criteria.skills()) {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<JobPost> job = sub.correlate(root);
            Join<JobPost, Skill> skill = job.join("requiredSkills");
            sub.select(cb.literal(1)).where(cb.equal(cb.lower(skill.get("name")), name));
            perSkill.add(cb.exists(sub));
        }
        where.add(criteria.skillsMatch() == SkillsMatch.ALL
                ? cb.and(perSkill.toArray(Predicate[]::new))
                : cb.or(perSkill.toArray(Predicate[]::new)));
    }

    private static List<Order> order(JobSearchCriteria criteria, Root<JobPost> root, CriteriaBuilder cb) {
        Expression<Integer> id = root.get("postId");
        return switch (criteria.sort()) {
            case NEWEST -> List.of(cb.desc(id));
            case OLDEST -> List.of(cb.asc(id));
            case TITLE -> List.of(cb.asc(cb.lower(root.get("jobTitle"))), cb.desc(id));
            case RATE_DESC -> List.of(contractTotalLast(root, cb), cb.desc(hourlyEquivalent(root, cb)), cb.desc(id));
            case RATE_ASC -> List.of(contractTotalLast(root, cb), cb.asc(hourlyEquivalent(root, cb)), cb.desc(id));
        };
    }

    /** 0 for every comparable job and 1 for a contract total, ascending, so those always come last. */
    private static Order contractTotalLast(Root<JobPost> root, CriteriaBuilder cb) {
        Expression<Integer> flag = cb.<Integer>selectCase()
                .when(cb.equal(root.get("rateType"), RateType.CONTRACT_TOTAL), 1)
                .otherwise(0);
        return cb.asc(flag);
    }

    /** Lower-cases the term and escapes LIKE wildcards. */
    static String likePattern(String term) {
        String escaped = term.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
