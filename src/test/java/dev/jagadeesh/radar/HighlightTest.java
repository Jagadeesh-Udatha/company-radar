package dev.jagadeesh.radar;

import dev.jagadeesh.radar.search.CompanySearchTestAccess;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HighlightTest {

    @Test
    void marksWordsThatStartWithAQueryTermIgnoringCaseAndPlural() {
        assertThat(CompanySearchTestAccess.highlight("Accept UPI payments. Payment links too.", "payments"))
                .isEqualTo("Accept UPI <em>payments</em>. <em>Payment</em> links too.");
    }

    @Test
    void leavesTextAloneWithoutAQuery() {
        assertThat(CompanySearchTestAccess.highlight("Fleet tracking", null)).isEqualTo("Fleet tracking");
    }
}
