package com.mcverse.jobify.company.repository;

import com.mcverse.jobify.company.model.CompanyInvitation;
import com.mcverse.jobify.company.model.InvitationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, String> {

    @EntityGraph(attributePaths = "company")
    List<CompanyInvitation> findByInviteeUsernameAndStatusOrderByCreatedAtDesc(String inviteeUsername,
                                                                              InvitationStatus status);

    @EntityGraph(attributePaths = "company")
    List<CompanyInvitation> findByCompanyIdAndStatusOrderByCreatedAtDesc(String companyId, InvitationStatus status);

    List<CompanyInvitation> findByCompanyIdAndInviteeUsernameAndStatus(String companyId, String inviteeUsername,
                                                                       InvitationStatus status);

    int deleteByInviteeUsername(String inviteeUsername);
}
