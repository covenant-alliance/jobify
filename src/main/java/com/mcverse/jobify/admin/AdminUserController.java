package com.mcverse.jobify.admin;

import com.mcverse.jobify.admin.dto.AdminUserSummary;
import com.mcverse.jobify.admin.service.AdminUserService;
import com.mcverse.jobify.auth.model.Role;
import com.mcverse.jobify.common.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Admin-only endpoints — require ADMIN role")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    @Operation(summary = "Search and page through accounts",
            description = "ADMIN only. Always paged. `q` matches the username or the profile's first or last name " +
                    "(contains, any case). Every call is written to the audit log.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of accounts"),
            @ApiResponse(responseCode = "400", description = "Invalid page, size, sort or role"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Not an admin"),
    })
    public PagedResponse<AdminUserSummary> users(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Text to look for in username, first name or last name") @RequestParam(required = false) String q,
            @Parameter(description = "Only this role") @RequestParam(required = false) Role role,
            @Parameter(description = "username (default), role, newest or oldest") @RequestParam(required = false) String sort,
            @Parameter(description = "Page number, from 0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1 to 100 (default 20)") @RequestParam(required = false) Integer size) {
        return adminUserService.search(q, role, sort, page, size, principal.getUsername());
    }
}
