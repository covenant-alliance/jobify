package com.mcverse.jobify.job;

import com.mcverse.jobify.job.model.RateType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The hourly equivalent of a rate (monthly / 173.33, yearly / 2080, same as the front end). It is no longer stored or
 * returned; it is kept, and tested, because server-side search on a minimum rate (issue #13) compares on it.
 */
class RateTypeTest {

    @Test
    void convertsEachUnitToAnHourlyAmountRoundedToCents() {
        assertEquals(75.5, RateType.HOURLY.toHourly(75.5));
        assertEquals(30.0, RateType.MONTHLY.toHourly(5200));
        assertEquals(34.62, RateType.MONTHLY.toHourly(6000));
        assertEquals(50.0, RateType.YEARLY.toHourly(104000));
    }

    @Test
    void aContractTotalCannotBeComparedSoItIsZero() {
        assertEquals(0.0, RateType.CONTRACT_TOTAL.toHourly(20000));
    }
}
