package com.example.fraudidentity.entity;

import com.example.fraudidentity.enums.ReviewStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "flagged_transactions")
public class FlaggedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "flagged_transaction_rules",
            joinColumns = @JoinColumn(name = "flagged_transaction_id"),
            inverseJoinColumns = @JoinColumn(name = "rule_id")
    )
    private Set<Rule> triggeredRules = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime flaggedAt;

    private LocalDateTime reviewedAt;

    public FlaggedTransaction() {
    }

    public FlaggedTransaction(Transaction transaction, Set<Rule> triggeredRules, ReviewStatus reviewStatus, LocalDateTime flaggedAt) {
        this.transaction = transaction;
        this.triggeredRules = triggeredRules;
        this.reviewStatus = reviewStatus;
        this.flaggedAt = flaggedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Set<Rule> getTriggeredRules() {
        return triggeredRules;
    }

    public void setTriggeredRules(Set<Rule> triggeredRules) {
        this.triggeredRules = triggeredRules;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public LocalDateTime getFlaggedAt() {
        return flaggedAt;
    }

    public void setFlaggedAt(LocalDateTime flaggedAt) {
        this.flaggedAt = flaggedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
