package com.aiquote.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiquote.backend.dashboard.DashboardRange.Bounds;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Covers Etap 18's date-range resolution, including the timezone boundary requirement
 * explicitly called out in the test list. */
class DashboardRangeTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    @Test
    void todaySpansExactlyTheCalendarDayInTheGivenZone() {
        Bounds bounds = DashboardRange.TODAY.resolve(ZONE);

        Instant expectedFrom = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        Instant expectedTo = LocalDate.now(ZONE).plusDays(1).atStartOfDay(ZONE).toInstant();
        assertThat(bounds.from()).isEqualTo(expectedFrom);
        assertThat(bounds.to()).isEqualTo(expectedTo);
        assertThat(Duration.between(bounds.from(), bounds.to())).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void last7DaysSpansSevenCalendarDaysIncludingToday() {
        Bounds bounds = DashboardRange.LAST_7_DAYS.resolve(ZONE);

        assertThat(Duration.between(bounds.from(), bounds.to())).isEqualTo(Duration.ofDays(7));
        assertThat(bounds.to()).isEqualTo(LocalDate.now(ZONE).plusDays(1).atStartOfDay(ZONE).toInstant());
    }

    @Test
    void last30DaysSpansThirtyCalendarDaysIncludingToday() {
        Bounds bounds = DashboardRange.LAST_30_DAYS.resolve(ZONE);

        assertThat(Duration.between(bounds.from(), bounds.to())).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void resolvingInDifferentZonesCanProduceDifferentBoundaries() {
        // The whole point of resolving in a given ZoneId — "today" in Tokyo starts
        // hours before "today" in Warsaw, so the two zones' TODAY bounds must differ
        // whenever the wall-clock moment sits in that gap.
        Bounds warsaw = DashboardRange.TODAY.resolve(ZoneId.of("Europe/Warsaw"));
        Bounds tokyo = DashboardRange.TODAY.resolve(ZoneId.of("Asia/Tokyo"));

        assertThat(warsaw.from()).isNotEqualTo(tokyo.from());
    }

    @Test
    void parseFallsBackToTodayForUnknownOrMissingValues() {
        assertThat(DashboardRange.parse(null)).isEqualTo(DashboardRange.TODAY);
        assertThat(DashboardRange.parse("")).isEqualTo(DashboardRange.TODAY);
        assertThat(DashboardRange.parse("not-a-range")).isEqualTo(DashboardRange.TODAY);
        assertThat(DashboardRange.parse("last_7_days")).isEqualTo(DashboardRange.LAST_7_DAYS);
    }
}
