package com.mcverse.jobify.admin.service;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.admin.dto.AdminUserSummary;
import com.mcverse.jobify.auth.model.AppUser;
import com.mcverse.jobify.auth.model.Role;
import com.mcverse.jobify.auth.repository.AuthUserRepository;
import com.mcverse.jobify.common.response.PagedResponse;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.model.Seeker;
import com.mcverse.jobify.user.model.User;
import com.mcverse.jobify.user.repository.EmployerRepository;
import com.mcverse.jobify.user.repository.SeekerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 100;

    private final AuthUserRepository authUserRepo;
    private final SeekerRepository seekerRepo;
    private final EmployerRepository employerRepo;
    private final AuditLog auditLog;

    public AdminUserService(AuthUserRepository authUserRepo, SeekerRepository seekerRepo,
                            EmployerRepository employerRepo, AuditLog auditLog) {
        this.authUserRepo = authUserRepo;
        this.seekerRepo = seekerRepo;
        this.employerRepo = employerRepo;
        this.auditLog = auditLog;
    }

    /**
     * One page of accounts. {@code q} matches the username or the profile's first or last name (contains, any case);
     * {@code role} narrows to one role; {@code sort} is one of username, role, newest, oldest.
     */
    @Transactional(readOnly = true)
    public PagedResponse<AdminUserSummary> search(String q, Role role, String sort, Integer page, Integer size,
                                                  String adminUsername) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0) {
            throw new IllegalArgumentException("page must be 0 or more.");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE + ".");
        }
        String query = q == null ? "" : q.trim();
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("q must be at most " + MAX_QUERY_LENGTH + " characters.");
        }

        List<Role> roles = role == null ? Arrays.asList(Role.values()) : List.of(role);
        Page<AppUser> result = authUserRepo.search(roles, likePattern(query),
                PageRequest.of(pageNumber, pageSize, sortFor(sort)));

        auditLog.record(adminUsername, "LIST_USERS", "q='" + query + "' role=" + role + " sort=" + sort
                + " page=" + pageNumber + " size=" + pageSize + " found=" + result.getTotalElements());
        return PagedResponse.of(toSummaries(result.getContent()), pageNumber, pageSize, result.getTotalElements());
    }

    private List<AdminUserSummary> toSummaries(List<AppUser> users) {
        List<String> usernames = users.stream().map(AppUser::getUsername).toList();
        Map<String, User> profiles = new HashMap<>();
        for (Seeker seeker : seekerRepo.findAllByUsernameIn(usernames)) {
            profiles.put(seeker.getUsername(), seeker);
        }
        for (Employer employer : employerRepo.findAllByUsernameIn(usernames)) {
            profiles.put(employer.getUsername(), employer);
        }
        return users.stream().map(u -> {
            User profile = profiles.get(u.getUsername());
            return new AdminUserSummary(u.getId(), u.getUsername(), u.getRole().name(),
                    profile == null ? null : profile.getName(),
                    profile == null ? null : profile.getLastName(),
                    profile == null ? null : profile.getId(),
                    profile == null ? null : profile.getCreationDate());
        }).collect(Collectors.toList());
    }

    private static Sort sortFor(String sort) {
        String key = sort == null || sort.isBlank() ? "username" : sort.trim();
        return switch (key) {
            case "username" -> Sort.by("username");
            case "role" -> Sort.by("role").and(Sort.by("username"));
            case "newest" -> Sort.by(Sort.Direction.DESC, "id");
            case "oldest" -> Sort.by(Sort.Direction.ASC, "id");
            default -> throw new IllegalArgumentException("sort must be one of: username, role, newest, oldest.");
        };
    }

    /** Lower-cases the term and escapes LIKE wildcards, so a search for "50%" means exactly that. */
    static String likePattern(String term) {
        String escaped = term.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
