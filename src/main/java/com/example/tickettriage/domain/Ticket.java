package com.example.tickettriage.domain;

import com.example.tickettriage.triage.TriageResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persistent incident ticket. A ticket is created in {@link TriageStatus#PENDING}
 * with no severity; the triage-derived fields ({@code severity},
 * {@code suggestedQueue}, {@code rationale}) are populated asynchronously once a
 * {@link com.example.tickettriage.triage.TriageClient} has classified it.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 10_000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Severity severity;

    @Column(name = "suggested_queue", length = 100)
    private String suggestedQueue;

    @Column(length = 1000)
    private String rationale;

    @Enumerated(EnumType.STRING)
    @Column(name = "triage_status", nullable = false, length = 16)
    private TriageStatus triageStatus = TriageStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Ticket() {
        // Required by JPA.
    }

    /**
     * Create a freshly received ticket, pending triage.
     */
    public Ticket(String subject, String body) {
        this.subject = subject;
        this.body = body;
        this.triageStatus = TriageStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (triageStatus == null) {
            triageStatus = TriageStatus.PENDING;
        }
    }

    /**
     * Apply a successful triage verdict and mark the ticket completed.
     */
    public void applyTriage(TriageResult result) {
        this.severity = result.severity();
        this.suggestedQueue = result.suggestedQueue();
        this.rationale = result.rationale();
        this.triageStatus = TriageStatus.COMPLETED;
    }

    /**
     * Mark triage as failed, recording why in the rationale.
     */
    public void markTriageFailed(String reason) {
        this.triageStatus = TriageStatus.FAILED;
        this.rationale = reason;
    }

    public Long getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getSuggestedQueue() {
        return suggestedQueue;
    }

    public void setSuggestedQueue(String suggestedQueue) {
        this.suggestedQueue = suggestedQueue;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public TriageStatus getTriageStatus() {
        return triageStatus;
    }

    public void setTriageStatus(TriageStatus triageStatus) {
        this.triageStatus = triageStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
