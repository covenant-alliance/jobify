package com.mcverse.jobify.job.event;

/**
 * A job was posted. Published inside the transaction that saved it; listeners that must only act on a job that really
 * exists (and must not slow the request down) use {@code @TransactionalEventListener(AFTER_COMMIT)} and {@code @Async}.
 */
public record JobPostedEvent(Integer jobId) {}
