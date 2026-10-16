package com.mcverse.jobify.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.common.exception.LicenseValidationException;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.match.MatchFacts.MatchResult;
import com.mcverse.jobify.match.MatchFacts.RankedJob;
import com.mcverse.jobify.match.MatchFacts.SeekerFacts;
import com.mcverse.jobify.match.MatchFactsLoader.JobWithFacts;
import com.mcverse.jobify.user.model.Seeker;
import com.mcverse.jobify.user.repository.SeekerRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The branches an end-to-end test cannot reach easily: nothing to rank, and an engine that answers with a stranger. */
class MatchServiceUnitTest {

    private final SeekerRepository seekers = mock(SeekerRepository.class);
    private final MatchFactsLoader facts = mock(MatchFactsLoader.class);
    private final MatchEngine engine = mock(MatchEngine.class);
    private final Seeker seeker = mock(Seeker.class);
    private MatchService service;

    @BeforeEach
    void setUp() {
        MatchProperties props = new MatchProperties(true, "t", "t", Duration.ofMillis(800), Duration.ofSeconds(2), 200, 500, null);
        service = new MatchService(seekers, mock(JobRepo.class), facts, engine, mock(AuditLog.class), props, mock(MatchJobAccess.class));
        when(seekers.findByUsername("sam")).thenReturn(Optional.of(seeker));
        when(seeker.getId()).thenReturn("sid");
        when(facts.preferredCountries(seeker)).thenReturn(List.of());
    }

    @Test
    void nothingToRankMeansAnEmptyPageAndNoCallToTheEngine() {
        when(facts.narrow(anyString(), anyList(), anyInt())).thenReturn(List.of());

        var page = service.recommend("sam", null, null);

        assertEquals(0, page.content().size());
        assertEquals(0, page.totalElements());
        assertEquals(20, page.size());
        verifyNoInteractions(engine);
        verify(facts, never()).jobs(any());
    }

    @Test
    void aJobTheEngineReturnsThatWeNeverSentIsIgnored() {
        when(facts.narrow(anyString(), anyList(), anyInt())).thenReturn(List.of(1));
        JobFacts one = new JobFacts(1, "one", List.of(), null, null, null, null, null, 0);
        when(facts.jobs(any())).thenReturn(List.of(new JobWithFacts(one, null)));
        when(facts.seeker(seeker)).thenReturn(new SeekerFacts("sid", List.of(), null));
        when(engine.rankJobs(any(), anyList())).thenReturn(List.of(
                new RankedJob(999, new MatchResult(99, List.of(), List.of(), List.of())),
                new RankedJob(1, new MatchResult(10, List.of(), List.of(), List.of()))));

        var page = service.recommend("sam", 0, 10);

        assertEquals(1, page.content().size());
        assertEquals(10, page.content().get(0).score());
    }

    @Test
    void aNonSeekerIsForbidden() {
        when(seekers.findByUsername("emp")).thenReturn(Optional.empty());
        assertThrows(LicenseValidationException.class, () -> service.recommend("emp", 0, 10));
    }
}
