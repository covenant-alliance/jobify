package com.mcverse.jobify.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Out of the box nothing is scored for a new job: the feature costs matching calls, so it is opt-in. */
@SpringBootTest
class MatchNotifierDefaultTest {

    @Autowired
    private MatchNotifier notifier;
    @Autowired
    private MatchProperties properties;

    @Test
    void offByDefault() {
        assertFalse(properties.alerts().enabled());
        assertEquals(75, properties.alerts().threshold());
        assertEquals(200, properties.alerts().maxSeekers());
        assertEquals(0, notifier.notifyFor(1));
    }
}
