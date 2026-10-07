package com.mcverse.jobify.company;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Invitations, members, roles (#72), the company dashboard and activity feed (#73), team notifications (#74). */
@SpringBootTest
class CompanyTeamFlowIntegrationTest {

    private static final String PASSWORD = "Flow-Pass-4321";

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;
    private String ownerName, colleagueName, outsiderName;
    private String owner, colleague, outsider, companyId, outsiderCompanyId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ownerName = name("tf_owner");
        colleagueName = name("tf_col");
        outsiderName = name("tf_out");
        owner = register(ownerName, "EMPLOYER");
        colleague = register(colleagueName, "EMPLOYER");
        outsider = register(outsiderName, "EMPLOYER");
        companyId = createCompany(owner);
        outsiderCompanyId = createCompany(outsider);
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private static String name(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String register(String username, String role) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"firstName\":\"F\","
                                + "\"lastName\":\"L\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private String createCompany(String token) throws Exception {
        String body = mvc.perform(post("/users/companies").header(AUTHORIZATION, token)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Flow Co " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions inviteRaw(String token, String company, String username) throws Exception {
        return mvc.perform(post("/companies/" + company + "/invitations").header(AUTHORIZATION, token)
                .contentType(APPLICATION_JSON).content("{\"username\":\"" + username + "\"}"));
    }

    private String invite(String token, String username) throws Exception {
        String body = inviteRaw(token, companyId, username).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void accept(String token, String invitationId) throws Exception {
        mvc.perform(post("/invitations/" + invitationId + "/accept").header(AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    /** The colleague joins the company through the real invitation flow. */
    private void colleagueJoins() throws Exception {
        accept(colleague, invite(owner, colleagueName));
    }

    private int createJob(String token, String title) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"" + title + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\",\"location\":\"Berlin\",\"workMode\":\"REMOTE\","
                                + "\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private String seeker() throws Exception {
        return register(name("tf_sk"), "SEEKER");
    }

    private String apply(String seeker, int jobId) throws Exception {
        String body = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private int notificationCount(String token, String title) throws Exception {
        String body = mvc.perform(get("/notifications").header(AUTHORIZATION, token)).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.<List<String>>read(body, "$[?(@.title=='" + title + "')].title").size();
    }

    private void bad(ResultActions result, int status, String message) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    // -------------------------------------------------------------------------------- invite and accept

    @Test
    void anOwnerInvitesAndTheInviteeAcceptsAndBecomesAManager() throws Exception {
        inviteRaw(owner, companyId, colleagueName).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.inviteeUsername").value(colleagueName))
                .andExpect(jsonPath("$.invitedBy").value(ownerName))
                .andExpect(jsonPath("$.companyId").value(companyId))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
        assertEquals(1, notificationCount(colleague, "Invitation to join " + companyName(companyId)));

        mvc.perform(get("/invitations/me").header(AUTHORIZATION, colleague)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].companyName").value(companyName(companyId)));
        mvc.perform(get("/companies/" + companyId + "/invitations").header(AUTHORIZATION, owner))
                .andExpect(jsonPath("$.length()").value(1));
        String id = JsonPath.read(mvc.perform(get("/invitations/me").header(AUTHORIZATION, colleague))
                .andReturn().getResponse().getContentAsString(), "$[0].id");

        mvc.perform(post("/invitations/" + id + "/accept").header(AUTHORIZATION, colleague)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED")).andExpect(jsonPath("$.respondedAt").isNotEmpty());

        mvc.perform(get("/users/employers/me").header(AUTHORIZATION, colleague))
                .andExpect(jsonPath("$.company.id").value(companyId)).andExpect(jsonPath("$.companyRole").value("MANAGER"));
        mvc.perform(get("/invitations/me").header(AUTHORIZATION, colleague)).andExpect(jsonPath("$", empty()));
        mvc.perform(get("/companies/" + companyId + "/invitations").header(AUTHORIZATION, owner))
                .andExpect(jsonPath("$", empty()));
        assertEquals(1, notificationCount(owner, "A new colleague joined"));
        bad(mvc.perform(post("/invitations/" + id + "/accept").header(AUTHORIZATION, colleague)), 422,
                "This invitation was already accepted.");
    }

    private String companyName(String id) {
        return jdbc.queryForObject("select name from companies where id = ?", String.class, id);
    }

    @Test
    void declineAndCancel() throws Exception {
        String declined = invite(owner, colleagueName);
        mvc.perform(post("/invitations/" + declined + "/decline").header(AUTHORIZATION, colleague))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED"));
        assertEquals(1, notificationCount(owner, "Invitation declined"));
        mvc.perform(get("/users/employers/me").header(AUTHORIZATION, colleague))
                .andExpect(jsonPath("$.company").doesNotExist());
        bad(mvc.perform(post("/invitations/" + declined + "/accept").header(AUTHORIZATION, colleague)), 422,
                "This invitation was already declined.");

        String cancelled = invite(owner, colleagueName); // after a decline the owner may ask again
        mvc.perform(delete("/companies/" + companyId + "/invitations/" + cancelled).header(AUTHORIZATION, owner))
                .andExpect(status().isNoContent());
        bad(mvc.perform(post("/invitations/" + cancelled + "/accept").header(AUTHORIZATION, colleague)), 422,
                "This invitation was already cancelled.");
        bad(mvc.perform(delete("/companies/" + companyId + "/invitations/" + cancelled).header(AUTHORIZATION, owner)),
                422, "This invitation was already cancelled.");
        mvc.perform(delete("/companies/" + companyId + "/invitations/" + UUID.randomUUID()).header(AUTHORIZATION, owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void whoCanInviteAndWhoCanBeInvited() throws Exception {
        colleagueJoins();
        bad(inviteRaw(colleague, companyId, outsiderName), 403, "Only the company owner can do that."); // a manager
        bad(inviteRaw(outsider, companyId, ownerName), 403, "You are not part of this company.");
        inviteRaw(owner, UUID.randomUUID().toString(), outsiderName).andExpect(status().isNotFound());

        String seeker = name("tf_sk2");
        register(seeker, "SEEKER");
        bad(inviteRaw(owner, companyId, seeker), 422, "No employer account is named '" + seeker + "'.");
        String ghost = "nobody_" + UUID.randomUUID().toString().substring(0, 6);
        bad(inviteRaw(owner, companyId, ghost), 422, "No employer account is named '" + ghost + "'.");
    }

    @Test
    void invitationRulesAreEnforcedWithReadableMessages() throws Exception {
        bad(inviteRaw(owner, companyId, ownerName), 422, "You cannot invite yourself.");
        bad(inviteRaw(owner, companyId, outsiderName), 422, outsiderName + " already belongs to a company.");
        String newcomer = name("tf_new");
        register(newcomer, "EMPLOYER");
        invite(owner, newcomer);
        bad(inviteRaw(owner, companyId, newcomer), 422, newcomer + " already has a pending invitation.");
        mvc.perform(post("/companies/" + companyId + "/invitations").header(AUTHORIZATION, owner)
                .contentType(APPLICATION_JSON).content("{\"username\":\"  \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/companies/" + companyId + "/invitations").header(AUTHORIZATION, owner)
                .contentType(APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        // someone who is already a member
        colleagueJoins();
        bad(inviteRaw(owner, companyId, colleagueName), 422, colleagueName + " is already a member of your company.");
    }

    @Test
    void onlyTheInvitedPersonAnswersAndOnlyEmployersHaveInvitations() throws Exception {
        String id = invite(owner, colleagueName);
        mvc.perform(post("/invitations/" + id + "/accept").header(AUTHORIZATION, outsider)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This invitation is not addressed to you."));
        mvc.perform(post("/invitations/" + id + "/decline").header(AUTHORIZATION, owner)).andExpect(status().isForbidden());
        mvc.perform(post("/invitations/" + UUID.randomUUID() + "/accept").header(AUTHORIZATION, colleague))
                .andExpect(status().isNotFound());
        String seeker = seeker();
        mvc.perform(get("/invitations/me").header(AUTHORIZATION, seeker)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only employers can have company invitations."));
        mvc.perform(post("/invitations/" + id + "/accept").header(AUTHORIZATION, seeker)).andExpect(status().isForbidden());
        mvc.perform(get("/invitations/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/invitations/" + id + "/accept")).andExpect(status().isUnauthorized());
        // nothing above changed it
        mvc.perform(get("/invitations/me").header(AUTHORIZATION, colleague)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anExpiredInvitationCannotBeAnsweredAndAnotherCanBeSent() throws Exception {
        String id = invite(owner, colleagueName);
        jdbc.update("update company_invitations set expires_at = ? where id = ?", LocalDateTime.now().minusDays(1), id);
        mvc.perform(get("/invitations/me").header(AUTHORIZATION, colleague)).andExpect(jsonPath("$", empty()));
        mvc.perform(get("/companies/" + companyId + "/invitations").header(AUTHORIZATION, owner))
                .andExpect(jsonPath("$", empty()));
        bad(mvc.perform(post("/invitations/" + id + "/accept").header(AUTHORIZATION, colleague)), 422,
                "This invitation has expired. Ask for a new one.");
        invite(owner, colleagueName); // the stale one does not block a new one
    }

    @Test
    void acceptingWhileInAnotherCompanyIsRefusedAndJoiningCancelsTheOtherInvitations() throws Exception {
        // an invitation sent while the person is free, answered after they started a company of their own
        String newcomerName = name("tf_late");
        String newcomer = register(newcomerName, "EMPLOYER");
        String toOwner = invite(owner, newcomerName);
        createCompany(newcomer);
        bad(mvc.perform(post("/invitations/" + toOwner + "/accept").header(AUTHORIZATION, newcomer)), 422,
                "You already belong to a company. Leave it before joining another.");

        // two companies invite the same free person: accepting one cancels the other
        String freeName = name("tf_free");
        String free = register(freeName, "EMPLOYER");
        String first = invite(owner, freeName);
        String second = JsonPath.read(inviteRaw(outsider, outsiderCompanyId, freeName).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        accept(free, first);
        bad(mvc.perform(post("/invitations/" + second + "/accept").header(AUTHORIZATION, free)), 422,
                "This invitation was already cancelled.");
    }

    // ----------------------------------------------------------------------------------------- members

    @Test
    void membersAreListedForMembersOnly() throws Exception {
        colleagueJoins();
        for (String who : new String[] {owner, colleague}) {
            mvc.perform(get("/companies/" + companyId + "/members").header(AUTHORIZATION, who))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].username").value(ownerName))
                    .andExpect(jsonPath("$[0].role").value("OWNER"))
                    .andExpect(jsonPath("$[1].username").value(colleagueName))
                    .andExpect(jsonPath("$[1].role").value("MANAGER"))
                    .andExpect(jsonPath("$[1].joinedAt").isNotEmpty());
        }
        mvc.perform(get("/companies/" + companyId + "/members").header(AUTHORIZATION, outsider))
                .andExpect(status().isForbidden());
        mvc.perform(get("/companies/" + UUID.randomUUID() + "/members").header(AUTHORIZATION, owner))
                .andExpect(status().isNotFound());
        mvc.perform(get("/companies/" + companyId + "/members")).andExpect(status().isUnauthorized());
    }

    @Test
    void anOwnerRemovesAMemberAndTheirJobsStayWithTheCompany() throws Exception {
        colleagueJoins();
        int job = createJob(colleague, "Posted by the removed one");
        mvc.perform(delete("/companies/" + companyId + "/members/" + colleagueName).header(AUTHORIZATION, colleague))
                .andExpect(status().isForbidden());
        bad(mvc.perform(delete("/companies/" + companyId + "/members/" + ownerName).header(AUTHORIZATION, owner)), 422,
                "To leave your own company use \"leave\" instead of removing yourself.");
        mvc.perform(delete("/companies/" + companyId + "/members/" + outsiderName).header(AUTHORIZATION, owner))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/companies/" + companyId + "/members/" + colleagueName).header(AUTHORIZATION, owner))
                .andExpect(status().isNoContent());

        mvc.perform(get("/jobs/" + job)).andExpect(jsonPath("$.employerUsername").value(ownerName));
        mvc.perform(get("/users/employers/me").header(AUTHORIZATION, colleague))
                .andExpect(jsonPath("$.company").doesNotExist()).andExpect(jsonPath("$.companyRole").doesNotExist());
        mvc.perform(put("/jobs/" + job).header(AUTHORIZATION, colleague).contentType(APPLICATION_JSON)
                .content("{\"jobTitle\":\"x\",\"jobDescription\":\"<p>x</p>\",\"rate\":1,\"rateType\":\"HOURLY\","
                        + "\"location\":\"B\",\"workMode\":\"REMOTE\",\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isForbidden());
        assertEquals(1, notificationCount(colleague, "You were removed from " + companyName(companyId)));
        // a removed person can start their own company or be invited again
        createCompany(colleague);
    }

    @Test
    void aMemberLeavesAndTheirJobsStay() throws Exception {
        colleagueJoins();
        int job = createJob(colleague, "Left behind");
        mvc.perform(delete("/companies/me/membership").header(AUTHORIZATION, colleague)).andExpect(status().isNoContent());
        mvc.perform(get("/jobs/" + job)).andExpect(jsonPath("$.employerUsername").value(ownerName));
        mvc.perform(get("/users/employers/me").header(AUTHORIZATION, colleague))
                .andExpect(jsonPath("$.company").doesNotExist());
        assertEquals(1, notificationCount(owner, "A colleague left"));
        bad(mvc.perform(delete("/companies/me/membership").header(AUTHORIZATION, colleague)), 422,
                "You are not part of a company.");
    }

    @Test
    void theOnlyOwnerCannotLeaveUntilAColleagueIsMadeOwner() throws Exception {
        bad(mvc.perform(delete("/companies/me/membership").header(AUTHORIZATION, owner)), 422,
                "You are the only person in this company, so you cannot leave it.");
        colleagueJoins();
        bad(mvc.perform(delete("/companies/me/membership").header(AUTHORIZATION, owner)), 422,
                "You are the only owner. Make a colleague an owner first, then leave.");
        mvc.perform(put("/companies/" + companyId + "/members/" + colleagueName + "/role").header(AUTHORIZATION, owner)
                        .contentType(APPLICATION_JSON).content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("OWNER"));
        mvc.perform(delete("/companies/me/membership").header(AUTHORIZATION, owner)).andExpect(status().isNoContent());
        mvc.perform(get("/companies/" + companyId + "/members").header(AUTHORIZATION, colleague))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void rolesChangeOnlyByOwnersAndACompanyKeepsAnOwner() throws Exception {
        colleagueJoins();
        String path = "/companies/" + companyId + "/members/" + colleagueName + "/role";
        mvc.perform(put(path).header(AUTHORIZATION, colleague).contentType(APPLICATION_JSON).content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).header(AUTHORIZATION, outsider).contentType(APPLICATION_JSON).content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).header(AUTHORIZATION, owner).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(path).header(AUTHORIZATION, owner).contentType(APPLICATION_JSON).content("{\"role\":\"BOSS\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/companies/" + companyId + "/members/" + outsiderName + "/role").header(AUTHORIZATION, owner)
                .contentType(APPLICATION_JSON).content("{\"role\":\"OWNER\"}")).andExpect(status().isNotFound());

        mvc.perform(put(path).header(AUTHORIZATION, owner).contentType(APPLICATION_JSON).content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isOk());
        assertEquals(1, notificationCount(colleague, "Your role changed"));
        // now two owners: one may step down, the last one may not
        mvc.perform(put("/companies/" + companyId + "/members/" + ownerName + "/role").header(AUTHORIZATION, owner)
                .contentType(APPLICATION_JSON).content("{\"role\":\"MANAGER\"}")).andExpect(status().isOk());
        bad(mvc.perform(put(path).header(AUTHORIZATION, colleague).contentType(APPLICATION_JSON)
                .content("{\"role\":\"MANAGER\"}")), 422, "A company needs at least one owner.");
        // asking for the role someone already has changes nothing
        mvc.perform(put(path).header(AUTHORIZATION, colleague).contentType(APPLICATION_JSON).content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isOk());
    }

    // ----------------------------------------------------------------------- dashboard and activity (#73)

    @Test
    void theActivityFeedShowsWhoDidWhatNewestFirst() throws Exception {
        colleagueJoins();
        int job = createJob(colleague, "Feed job");
        String applicationId = apply(seeker(), job);
        mvc.perform(put("/applications/" + applicationId + "/status").header(AUTHORIZATION, owner)
                .contentType(APPLICATION_JSON).content("{\"status\":\"IN_REVIEW\"}")).andExpect(status().isOk());
        mvc.perform(put("/jobs/" + job).header(AUTHORIZATION, owner).contentType(APPLICATION_JSON)
                .content("{\"jobTitle\":\"Feed job v2\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,\"rateType\":\"HOURLY\","
                        + "\"location\":\"Berlin\",\"workMode\":\"REMOTE\",\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isOk());
        for (boolean open : new boolean[] {false, true}) {
            mvc.perform(patch("/jobs/" + job + "/available").header(AUTHORIZATION, colleague)
                    .contentType(APPLICATION_JSON).content("{\"available\":" + open + "}")).andExpect(status().isOk());
        }
        // closing an already-closed job (no change) writes nothing
        mvc.perform(patch("/jobs/" + job + "/available").header(AUTHORIZATION, colleague)
                .contentType(APPLICATION_JSON).content("{\"available\":true}")).andExpect(status().isOk());

        for (String who : new String[] {owner, colleague}) {
            mvc.perform(get("/companies/" + companyId + "/activity").header(AUTHORIZATION, who)).andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].type", contains("JOB_REOPENED", "JOB_CLOSED", "JOB_EDITED",
                            "APPLICATION_MOVED", "JOB_POSTED", "MEMBER_JOINED", "INVITATION_SENT")))
                    .andExpect(jsonPath("$[0].actor").value(colleagueName))
                    .andExpect(jsonPath("$[0].summary").value(colleagueName + " reopened \"Feed job v2\""))
                    .andExpect(jsonPath("$[2].actor").value(ownerName))
                    .andExpect(jsonPath("$[3].summary", containsString("from APPLIED to IN_REVIEW")))
                    .andExpect(jsonPath("$[3].jobId").value(job))
                    .andExpect(jsonPath("$[3].applicationId").value(applicationId))
                    .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
        }
        mvc.perform(get("/companies/" + companyId + "/activity?limit=2").header(AUTHORIZATION, owner))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void membershipChangesAreInTheFeedToo() throws Exception {
        colleagueJoins();
        mvc.perform(put("/companies/" + companyId + "/members/" + colleagueName + "/role").header(AUTHORIZATION, owner)
                .contentType(APPLICATION_JSON).content("{\"role\":\"OWNER\"}")).andExpect(status().isOk());
        mvc.perform(delete("/companies/me/membership").header(AUTHORIZATION, colleague)).andExpect(status().isNoContent());
        String again = invite(owner, colleagueName);
        accept(colleague, again);
        mvc.perform(delete("/companies/" + companyId + "/members/" + colleagueName).header(AUTHORIZATION, owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/companies/" + companyId + "/activity").header(AUTHORIZATION, owner))
                .andExpect(jsonPath("$[*].type", hasItem("ROLE_CHANGED")))
                .andExpect(jsonPath("$[*].type", hasItem("MEMBER_LEFT")))
                .andExpect(jsonPath("$[*].type", hasItem("MEMBER_REMOVED")))
                .andExpect(jsonPath("$[0].type").value("MEMBER_REMOVED"));
    }

    @Test
    void theFeedAndTheNumbersAreForMembersOnlyAndTheLimitIsChecked() throws Exception {
        mvc.perform(get("/companies/" + companyId + "/activity").header(AUTHORIZATION, outsider))
                .andExpect(status().isForbidden());
        mvc.perform(get("/companies/" + companyId + "/stats").header(AUTHORIZATION, outsider))
                .andExpect(status().isForbidden());
        mvc.perform(get("/companies/" + UUID.randomUUID() + "/activity").header(AUTHORIZATION, owner))
                .andExpect(status().isNotFound());
        mvc.perform(get("/companies/" + companyId + "/activity")).andExpect(status().isUnauthorized());
        mvc.perform(get("/companies/" + companyId + "/stats").header(AUTHORIZATION, seeker()))
                .andExpect(status().isForbidden());
        for (String limit : new String[] {"0", "101"}) {
            bad(mvc.perform(get("/companies/" + companyId + "/activity?limit=" + limit).header(AUTHORIZATION, owner)),
                    400, "limit must be between 1 and 100.");
        }
        mvc.perform(get("/companies/" + companyId + "/activity?limit=100").header(AUTHORIZATION, owner))
                .andExpect(status().isOk());
    }

    @Test
    void companyStatsCoverEveryMembersJobs() throws Exception {
        colleagueJoins();
        int byOwner = createJob(owner, "Stats A");
        int byColleague = createJob(colleague, "Stats B");
        apply(seeker(), byOwner);
        apply(seeker(), byColleague);
        for (String who : new String[] {owner, colleague}) {
            mvc.perform(get("/companies/" + companyId + "/stats").header(AUTHORIZATION, who)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs.total").value(2)).andExpect(jsonPath("$.applications.total").value(2))
                    .andExpect(jsonPath("$.perJob.length()").value(2));
        }
    }

    @Test
    void aPersonWithoutACompanyHasNoFeedEntries() throws Exception {
        String loneName = name("tf_lone");
        String lone = register(loneName, "EMPLOYER");
        createJob(lone, "No company, no feed");
        assertEquals(0, jdbc.queryForObject("select count(*) from company_activity where actor = ?", Integer.class,
                loneName));
    }

    // ----------------------------------------------------------------------- team notifications (#74)

    @Test
    void everyoneInTheCompanyHearsAboutNewAndWithdrawnApplicationsThroughTheirOwnSettings() throws Exception {
        colleagueJoins();
        int job = createJob(owner, "Heard by all");
        String seeker = seeker();
        String applicationId = apply(seeker, job);
        assertEquals(1, notificationCount(owner, "New application"));
        assertEquals(1, notificationCount(colleague, "New application"));
        assertEquals(0, notificationCount(outsider, "New application"));

        mvc.perform(delete("/applications/" + applicationId).header(AUTHORIZATION, seeker))
                .andExpect(status().isNoContent());
        assertEquals(1, notificationCount(owner, "Application withdrawn"));
        assertEquals(1, notificationCount(colleague, "Application withdrawn"));
        assertEquals(0, notificationCount(outsider, "Application withdrawn"));
    }

    @Test
    void aSwitchedOffMemberGetsNoNewApplicationNotice() throws Exception {
        colleagueJoins();
        mvc.perform(put("/users/me/preferences").header(AUTHORIZATION, colleague).contentType(APPLICATION_JSON)
                .content("{\"notifications\":{\"APPLICATION\":false}}")).andExpect(status().isOk());
        apply(seeker(), createJob(owner, "Only the owner hears"));
        assertEquals(1, notificationCount(owner, "New application"));
        assertEquals(0, notificationCount(colleague, "New application"));
    }

    @Test
    void teamEventsNotifyThePeopleAffected() throws Exception {
        colleagueJoins();
        mvc.perform(delete("/companies/" + companyId + "/members/" + colleagueName).header(AUTHORIZATION, owner))
                .andExpect(status().isNoContent());
        // after the removal the colleague no longer hears about the company's applicants
        apply(seeker(), createJob(owner, "After removal"));
        assertEquals(0, notificationCount(colleague, "New application"));
    }

    // ------------------------------------------------------------------------------------- housekeeping

    @Test
    void deletingAnAccountRemovesItsInvitations() throws Exception {
        String id = invite(owner, colleagueName);
        assertEquals(1, jdbc.queryForObject("select count(*) from company_invitations where id = ?", Integer.class, id));
        String req = mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, colleague)
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"bye\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/admin/deletion-requests/" + (String) JsonPath.read(req, "$.id") + "/approve")
                .header(AUTHORIZATION, bearer(mvc, "admin"))).andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("select count(*) from company_invitations where id = ?", Integer.class, id));
        mvc.perform(get("/companies/" + companyId + "/invitations").header(AUTHORIZATION, owner))
                .andExpect(jsonPath("$[*].id", not(hasItem(id))));
    }
}
