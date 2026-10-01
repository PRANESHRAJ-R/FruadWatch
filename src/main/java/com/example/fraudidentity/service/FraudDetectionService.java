package com.example.fraudidentity.service;

import com.example.fraudidentity.entity.FlaggedTransaction;
import com.example.fraudidentity.entity.Rule;
import com.example.fraudidentity.entity.Transaction;
import com.example.fraudidentity.enums.ReviewStatus;
import com.example.fraudidentity.enums.RuleType;
import com.example.fraudidentity.enums.TransactionStatus;
import com.example.fraudidentity.repository.FlaggedTransactionRepository;
import com.example.fraudidentity.repository.RuleRepository;
import com.example.fraudidentity.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FraudDetectionService {

    private final TransactionRepository transactionRepository;
    private final RuleRepository ruleRepository;
    private final FlaggedTransactionRepository flaggedTransactionRepository;
    private final AccountHolderService accountHolderService;

    public FraudDetectionService(TransactionRepository transactionRepository,
                                 RuleRepository ruleRepository,
                                 FlaggedTransactionRepository flaggedTransactionRepository,
                                 AccountHolderService accountHolderService) {
        this.transactionRepository = transactionRepository;
        this.ruleRepository = ruleRepository;
        this.flaggedTransactionRepository = flaggedTransactionRepository;
        this.accountHolderService = accountHolderService;
    }

    public record ProcessResult(Transaction transaction, Set<Rule> triggeredRules) {}

    @Transactional
    public ProcessResult processTransaction(Transaction transaction) {
        // 1. Validation
        validateTransaction(transaction);

        if (transaction.getTimestamp() == null) {
            transaction.setTimestamp(LocalDateTime.now());
        }

        // 2. Fetch active rules
        List<Rule> activeRules = ruleRepository.findByEnabledTrue();
        Set<Rule> triggeredRules = new LinkedHashSet<>();

        for (Rule rule : activeRules) {
            if (rule.getRuleType() == RuleType.AMOUNT_THRESHOLD) {
                if (rule.getThresholdAmount() != null &&
                        transaction.getAmount().compareTo(rule.getThresholdAmount()) > 0) {
                    triggeredRules.add(rule);
                }
            } else if (rule.getRuleType() == RuleType.VELOCITY) {
                if (rule.getTransactionCount() != null && rule.getTimeWindowMinutes() != null) {
                    LocalDateTime windowStart = transaction.getTimestamp().minusMinutes(rule.getTimeWindowMinutes());
                    LocalDateTime windowEnd = transaction.getTimestamp();

                    long existingCount = transactionRepository.countBySenderAccountAndTimestampBetween(
                            transaction.getSenderAccount().trim(),
                            windowStart,
                            windowEnd
                    );
                    long totalCount = existingCount + 1; // including current transaction

                    if (totalCount > rule.getTransactionCount()) {
                        triggeredRules.add(rule);
                    }
                }
            }
        }

        // 3. Status determination and saving
        if (triggeredRules.isEmpty()) {
            transaction.setStatus(TransactionStatus.COMPLETED);
            Transaction savedTx = transactionRepository.save(transaction);
            accountHolderService.deductBalanceIfPossible(savedTx.getSenderAccount(), savedTx.getAmount());
            accountHolderService.creditBalance(savedTx.getReceiverAccount(), savedTx.getAmount());
            return new ProcessResult(savedTx, triggeredRules);
        } else {
            transaction.setStatus(TransactionStatus.PENDING_REVIEW);
            Transaction savedTx = transactionRepository.save(transaction);

            FlaggedTransaction flagged = new FlaggedTransaction();
            flagged.setTransaction(savedTx);
            flagged.setTriggeredRules(triggeredRules);
            flagged.setReviewStatus(ReviewStatus.PENDING);
            flagged.setFlaggedAt(LocalDateTime.now());
            flaggedTransactionRepository.save(flagged);

            return new ProcessResult(savedTx, triggeredRules);
        }
    }

    @Transactional
    public void approveFlaggedTransaction(Long flaggedTransactionId) {
        FlaggedTransaction flagged = flaggedTransactionRepository.findById(flaggedTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("Flagged transaction not found with id: " + flaggedTransactionId));

        if (flagged.getReviewStatus() == ReviewStatus.BLOCKED ||
                flagged.getTransaction().getStatus() == TransactionStatus.BLOCKED) {
            throw new IllegalStateException("Transaction cannot be approved because it has already been blocked.");
        }

        flagged.setReviewStatus(ReviewStatus.APPROVED);
        flagged.setReviewedAt(LocalDateTime.now());
        flagged.getTransaction().setStatus(TransactionStatus.COMPLETED);

        transactionRepository.save(flagged.getTransaction());
        flaggedTransactionRepository.save(flagged);

        accountHolderService.deductBalanceIfPossible(flagged.getTransaction().getSenderAccount(), flagged.getTransaction().getAmount());
        accountHolderService.creditBalance(flagged.getTransaction().getReceiverAccount(), flagged.getTransaction().getAmount());
    }

    @Transactional
    public void blockFlaggedTransaction(Long flaggedTransactionId) {
        FlaggedTransaction flagged = flaggedTransactionRepository.findById(flaggedTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("Flagged transaction not found with id: " + flaggedTransactionId));

        if (flagged.getReviewStatus() == ReviewStatus.APPROVED) {
            throw new IllegalStateException("Transaction cannot be blocked because it has already been approved.");
        }

        flagged.setReviewStatus(ReviewStatus.BLOCKED);
        flagged.setReviewedAt(LocalDateTime.now());
        flagged.getTransaction().setStatus(TransactionStatus.BLOCKED);

        transactionRepository.save(flagged.getTransaction());
        flaggedTransactionRepository.save(flagged);
    }

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAllByOrderByTimestampDesc();
    }

    public List<FlaggedTransaction> getAllFlaggedTransactions() {
        return flaggedTransactionRepository.findAllByOrderByFlaggedAtDesc();
    }

    public List<FlaggedTransaction> getRecentFlaggedTransactions() {
        return flaggedTransactionRepository.findTop10ByOrderByFlaggedAtDesc();
    }

    public Map<String, Long> getDashboardStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalTransactions", transactionRepository.count());
        stats.put("completedTransactions", transactionRepository.countByStatus(TransactionStatus.COMPLETED));
        stats.put("pendingTransactions", transactionRepository.countByStatus(TransactionStatus.PENDING_REVIEW));
        stats.put("blockedTransactions", transactionRepository.countByStatus(TransactionStatus.BLOCKED));
        stats.put("totalFlagged", flaggedTransactionRepository.count());
        stats.put("totalAccounts", accountHolderService.countAccountHolders());
        return stats;
    }

    private void validateTransaction(Transaction transaction) {
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than 0.");
        }
        if (transaction.getSenderAccount() == null || transaction.getSenderAccount().trim().isEmpty()) {
            throw new IllegalArgumentException("Sender account is required.");
        }
        if (transaction.getReceiverAccount() == null || transaction.getReceiverAccount().trim().isEmpty()) {
            throw new IllegalArgumentException("Receiver account is required.");
        }
        if (transaction.getSenderAccount().trim().equalsIgnoreCase(transaction.getReceiverAccount().trim())) {
            throw new IllegalArgumentException("Sender and receiver accounts cannot be the same.");
        }
    }
}
