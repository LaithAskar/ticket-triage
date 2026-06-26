package com.example.tickettriage.triage;

import com.example.tickettriage.domain.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the keyword-based classifier directly — this is the project's core
 * domain logic, so it gets its own tests rather than only being reached through
 * mocks.
 */
class StubTriageClientTest {

    private final StubTriageClient client = new StubTriageClient();

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {2} / {3}")
    @CsvSource(delimiter = '|', value = {
            "Production outage         | The whole site is down for everyone        | CRITICAL | On-Call",
            "Security breach detected  | possible data leak of customer records      | CRITICAL | Security",
            "Ransomware on file server | files encrypted ransomware note found        | CRITICAL | Security",
            "Login error               | users get a 500 error when signing in        | HIGH     | General-Support",
            "Database problems         | queries are failing with timeout errors      | HIGH     | Database",
            "VPN slow                  | connection is intermittent and slow          | MEDIUM   | Network",
            "General question          | how do I export a monthly summary            | LOW      | General-Support"
    })
    void classifiesSeverityAndRoutesQueue(String subject, String body,
                                          Severity expectedSeverity, String expectedQueue) {
        TriageResult result = client.triage(subject, body);

        assertThat(result.severity()).isEqualTo(expectedSeverity);
        assertThat(result.suggestedQueue()).isEqualTo(expectedQueue);
        assertThat(result.rationale()).isNotBlank();
    }

    @Test
    void defaultsToLowWhenNoKeywordsMatch() {
        TriageResult result = client.triage("Hello", "Just saying hi to the team");

        assertThat(result.severity()).isEqualTo(Severity.LOW);
        assertThat(result.suggestedQueue()).isEqualTo("General-Support");
    }

    @Test
    void isNullSafeOnSubject() {
        TriageResult result = client.triage(null, "the server is down");

        assertThat(result.severity()).isEqualTo(Severity.CRITICAL);
    }
}
