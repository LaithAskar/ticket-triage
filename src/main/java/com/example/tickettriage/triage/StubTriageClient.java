package com.example.tickettriage.triage;

import com.example.tickettriage.domain.Severity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Offline, deterministic triage based on keyword matching. This is the default
 * implementation ({@code triage.mode=stub}, or absent) so the service runs with
 * no network dependency.
 */
@Component
@ConditionalOnProperty(name = "triage.mode", havingValue = "stub", matchIfMissing = true)
public class StubTriageClient implements TriageClient {

    private static final List<String> CRITICAL_KEYWORDS = List.of(
            "outage", "down", "data loss", "breach", "ransomware", "data leak",
            "production is down", "cannot access", "everyone", "all users", "sev1", "p1");

    private static final List<String> HIGH_KEYWORDS = List.of(
            "error", "failed", "failure", "crash", "cannot login", "can't login",
            "unable to", "degraded", "timeout", "500", "exception", "blocked");

    private static final List<String> MEDIUM_KEYWORDS = List.of(
            "slow", "intermittent", "occasionally", "sometimes", "delay", "warning");

    private static final List<String> SECURITY_KEYWORDS = List.of(
            "breach", "ransomware", "phishing", "malware", "password", "unauthorized",
            "vulnerability", "data leak", "data loss");

    private static final List<String> NETWORK_KEYWORDS = List.of(
            "network", "vpn", "dns", "wifi", "wi-fi", "connectivity", "firewall", "latency");

    private static final List<String> DATABASE_KEYWORDS = List.of(
            "database", "db", "sql", "query", "deadlock", "replication");

    @Override
    public TriageResult triage(String subject, String body) {
        String haystack = ((subject == null ? "" : subject) + " " + (body == null ? "" : body))
                .toLowerCase(Locale.ROOT);

        Severity severity = classifySeverity(haystack);
        String queue = routeQueue(haystack, severity);
        String rationale = String.format(
                "Rule-based triage: classified %s and routed to '%s' based on keyword analysis.",
                severity, queue);

        return new TriageResult(severity, queue, rationale);
    }

    private Severity classifySeverity(String haystack) {
        if (containsAny(haystack, CRITICAL_KEYWORDS)) {
            return Severity.CRITICAL;
        }
        if (containsAny(haystack, HIGH_KEYWORDS)) {
            return Severity.HIGH;
        }
        if (containsAny(haystack, MEDIUM_KEYWORDS)) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }

    private String routeQueue(String haystack, Severity severity) {
        if (containsAny(haystack, SECURITY_KEYWORDS)) {
            return "Security";
        }
        if (containsAny(haystack, NETWORK_KEYWORDS)) {
            return "Network";
        }
        if (containsAny(haystack, DATABASE_KEYWORDS)) {
            return "Database";
        }
        // High-urgency, unclassified issues escalate to on-call; the rest go to general support.
        return severity == Severity.CRITICAL ? "On-Call" : "General-Support";
    }

    private boolean containsAny(String haystack, List<String> needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
