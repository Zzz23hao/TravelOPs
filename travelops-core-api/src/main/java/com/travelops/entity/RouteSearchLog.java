package com.travelops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Write-only audit row. user_id is a plain column rather than a @ManyToOne: writing a log
 * never needs the User object, so it never loads one.
 */
@Entity
@Table(name = "route_search_logs")
public class RouteSearchLog {

    public enum Outcome { FOUND, NOT_FOUND, REJECTED, UNAVAILABLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_airport", nullable = false, length = 3)
    private String sourceAirport;

    @Column(name = "destination_airport", nullable = false, length = 3)
    private String destinationAirport;

    @Column(name = "search_type", nullable = false, length = 20)
    private String searchType;

    // STRING, not the default ORDINAL: reordering the enum must not silently change stored rows.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    @Column(name = "result_summary", nullable = false, columnDefinition = "text")
    private String resultSummary;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RouteSearchLog() {
    }

    public RouteSearchLog(Long userId, String sourceAirport, String destinationAirport, String searchType,
                          Outcome outcome, String resultSummary, int latencyMs) {
        this.userId = userId;
        this.sourceAirport = sourceAirport;
        this.destinationAirport = destinationAirport;
        this.searchType = searchType;
        this.outcome = outcome;
        this.resultSummary = resultSummary;
        this.latencyMs = latencyMs;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSourceAirport() {
        return sourceAirport;
    }

    public String getDestinationAirport() {
        return destinationAirport;
    }

    public String getSearchType() {
        return searchType;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
