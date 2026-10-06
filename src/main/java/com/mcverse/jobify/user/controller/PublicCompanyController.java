package com.mcverse.jobify.user.controller;

import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.user.dto.CompanyResponse;
import com.mcverse.jobify.user.repository.CompanyRepository;
import com.mcverse.jobify.common.storage.ImageResponses;
import com.mcverse.jobify.user.service.CompanyLogoService;
import com.mcverse.jobify.user.service.CompanyLogos;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import java.time.Duration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Companies", description = "Public company information")
@RestController
@RequestMapping("/companies")
public class PublicCompanyController {

    private final CompanyRepository companyRepository;

    private final CompanyLogoService logos;

    public PublicCompanyController(CompanyRepository companyRepository, CompanyLogoService logos) {
        this.companyRepository = companyRepository;
        this.logos = logos;
    }

    @Operation(summary = "Download a company's logo",
            description = "Public. PNG, JPEG or WebP; cacheable for a day. `logoUrl` on the company and on jobs points here "
                    + "and changes whenever the logo does.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The image"),
            @ApiResponse(responseCode = "404", description = "No such company, or it has no logo"),
    })
    @SecurityRequirements
    @GetMapping("/{id}/logo")
    public ResponseEntity<Resource> getLogo(@Parameter(description = "Company id (UUID)") @PathVariable String id) {
        var logo = logos.read(id);
        return ImageResponses.of(logo.file(), logo.contentType(), Duration.ofDays(1));
    }

    @Operation(summary = "Get a company", description = "Public endpoint — no authentication required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The company",
                    content = @Content(schema = @Schema(implementation = CompanyResponse.class))),
            @ApiResponse(responseCode = "404", description = "Company not found"),
    })
    @SecurityRequirements
    @GetMapping("/{id}")
    public CompanyResponse getCompany(@Parameter(description = "Company id (UUID)") @PathVariable String id) {
        return companyRepository.findById(id)
                .map(c -> new CompanyResponse(c.getId(), c.getName(), CompanyLogos.urlOf(c)))
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));
    }
}
