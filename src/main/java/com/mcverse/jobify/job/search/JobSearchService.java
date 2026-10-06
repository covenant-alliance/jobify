package com.mcverse.jobify.job.search;

import com.mcverse.jobify.common.response.PagedResponse;
import com.mcverse.jobify.job.dto.JobPostResponse;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.job.service.JobResponseMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a job search in two steps so paging stays correct and cheap: first one page of matching jobs in the requested
 * order (the query carries the filters, the sort and the exact count), then one query that loads that page's jobs
 * with their employer, company and skills, instead of one query per job.
 */
@Service
public class JobSearchService {

    private final JobRepo jobRepo;
    private final JobResponseMapper mapper;

    public JobSearchService(JobRepo jobRepo, JobResponseMapper mapper) {
        this.jobRepo = jobRepo;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PagedResponse<JobPostResponse> search(JobSearchCriteria criteria) {
        // unsorted on purpose: the specification sets the order (see JobSearchSpecifications)
        Page<JobPost> matches = jobRepo.findAll(JobSearchSpecifications.matching(criteria),
                PageRequest.of(criteria.page(), criteria.size()));

        List<Integer> ids = matches.getContent().stream().map(JobPost::getPostId).toList();
        Map<Integer, JobPost> loaded = new HashMap<>();
        if (!ids.isEmpty()) {
            jobRepo.findAllByPostIdIn(ids).forEach(job -> loaded.put(job.getPostId(), job));
        }
        List<JobPostResponse> content = ids.stream().map(loaded::get).map(mapper::toResponse).toList();
        return PagedResponse.of(content, criteria.page(), criteria.size(), matches.getTotalElements());
    }
}
