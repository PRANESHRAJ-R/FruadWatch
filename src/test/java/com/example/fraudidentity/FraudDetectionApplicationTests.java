package com.example.fraudidentity;

import com.example.fraudidentity.entity.FlaggedTransaction;
import com.example.fraudidentity.entity.Rule;
import com.example.fraudidentity.entity.Transaction;
import com.example.fraudidentity.enums.ReviewStatus;
import com.example.fraudidentity.enums.RuleType;
import com.example.fraudidentity.enums.TransactionStatus;
import com.example.fraudidentity.repository.FlaggedTransactionRepository;
import com.example.fraudidentity.repository.RuleRepository;
import com.example.fraudidentity.repository.TransactionRepository;
import com.example.fraudidentity.service.FraudDetectionService;
import com.example.fraudidentity.service.RuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FraudDetectionApplicationTests {

    @Autowired
    private FraudDetectionService fraudDetectionService;

    @Autowired
    private RuleService ruleService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private FlaggedTransactionRepository flaggedTransactionRepository;

    @BeforeEach
    void setUp() {
        flaggedTransactionRepository.deleteAll();
        transactionRepository.deleteAll();
        ruleRepository.deleteAll();
        // re-initialize default rules
        ruleService.initDefaultRules();
    }

    @Test
    @DisplayName("Default rules are properly created on startup")
    void testDefaultRulesCreated() {
        List<Rule> rules = ruleService.getAllRules();
        assertEquals(2, rules.size(), "Should have exactly 2 default rules");
        assertTrue(ruleRepository.existsByName("Large Amount Rule"));
        assertTrue(ruleRepository.existsByName("Rapid Transactions Rule"));
    }

    @Test
    @DisplayName("TEST 1: Create a normal transaction below the threshold -> COMPLETED")
    void test1_NormalTransactionBelowThreshold() {
        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal("1000.00"));
        tx.setSenderAccount("USER_A");
        tx.setReceiverAccount("USER_B");
        tx.setTimestamp(LocalDateTime.now());

        FraudDetectionService.ProcessResult result = fraudDetectionService.processTransaction(tx);

        assertNotNull(result.transaction().getId());
        assertEquals(TransactionStatus.COMPLETED, result.transaction().getStatus());
        assertTrue(result.triggeredRules().isEmpty(), "No rules should be triggered");
        assertEquals(0, flaggedTransactionRepository.count(), "No flagged transaction record should exist");
    }

    @Test
    @DisplayName("TEST 2: Create a transaction above the amount threshold -> PENDING_REVIEW and AMOUNT_THRESHOLD rule recorded")
    void test2_TransactionAboveAmountThreshold() {
        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal("75000.00")); // Above 50,000 default threshold
        tx.setSenderAccount("USER_A");
        tx.setReceiverAccount("USER_B");
        tx.setTimestamp(LocalDateTime.now());

        FraudDetectionService.ProcessResult result = fraudDetectionService.processTransaction(tx);

        assertEquals(TransactionStatus.PENDING_REVIEW, result.transaction().getStatus());
        assertEquals(1, result.triggeredRules().size());
        assertEquals(RuleType.AMOUNT_THRESHOLD, result.triggeredRules().iterator().next().getRuleType());

        List<FlaggedTransaction> flaggedList = flaggedTransactionRepository.findAll();
        assertEquals(1, flaggedList.size());
        FlaggedTransaction flagged = flaggedList.get(0);
        assertEquals(result.transaction().getId(), flagged.getTransaction().getId());
        assertEquals(ReviewStatus.PENDING, flagged.getReviewStatus());
        assertEquals(1, flagged.getTriggeredRules().size());
        assertEquals("Large Amount Rule", flagged.getTriggeredRules().iterator().next().getName());
    }

    @Test
    @DisplayName("TEST 3: Create multiple transactions from same sender within time window -> Velocity rule triggered")
    void test3_MultipleTransactionsWithinTimeWindow() {
        // Default rule: > 3 transactions within 10 minutes
        // Create 3 normal transactions first (<= 3 allowed)
        for (int i = 1; i <= 3; i++) {
            Transaction tx = new Transaction();
            tx.setAmount(new BigDecimal("500.00"));
            tx.setSenderAccount("SENDER_RAPID");
            tx.setReceiverAccount("RECEIVER_" + i);
            tx.setTimestamp(LocalDateTime.now().minusMinutes(5 - i));
            FraudDetectionService.ProcessResult res = fraudDetectionService.processTransaction(tx);
            assertEquals(TransactionStatus.COMPLETED, res.transaction().getStatus());
        }

        // 4th transaction within window -> should trigger VELOCITY rule
        Transaction tx4 = new Transaction();
        tx4.setAmount(new BigDecimal("500.00"));
        tx4.setSenderAccount("SENDER_RAPID");
        tx4.setReceiverAccount("RECEIVER_FINAL");
        tx4.setTimestamp(LocalDateTime.now());

        FraudDetectionService.ProcessResult res4 = fraudDetectionService.processTransaction(tx4);

        assertEquals(TransactionStatus.PENDING_REVIEW, res4.transaction().getStatus());
        assertEquals(1, res4.triggeredRules().size());
        assertEquals(RuleType.VELOCITY, res4.triggeredRules().iterator().next().getRuleType());
        assertEquals("Rapid Transactions Rule", res4.triggeredRules().iterator().next().getName());
    }

    @Test
    @DisplayName("TEST 4: Create a transaction that triggers BOTH rules -> PENDING_REVIEW and BOTH rules recorded")
    void test4_TransactionTriggersBothRules() {
        // First make 3 transactions to reach threshold
        for (int i = 1; i <= 3; i++) {
            Transaction tx = new Transaction();
            tx.setAmount(new BigDecimal("100.00"));
            tx.setSenderAccount("SENDER_DUAL");
            tx.setReceiverAccount("RECEIVER_" + i);
            tx.setTimestamp(LocalDateTime.now().minusMinutes(3 - i));
            fraudDetectionService.processTransaction(tx);
        }

        // 4th transaction: high amount (75,000) AND 4th transaction in window
        Transaction tx4 = new Transaction();
        tx4.setAmount(new BigDecimal("75000.00"));
        tx4.setSenderAccount("SENDER_DUAL");
        tx4.setReceiverAccount("RECEIVER_DUAL");
        tx4.setTimestamp(LocalDateTime.now());

        FraudDetectionService.ProcessResult res4 = fraudDetectionService.processTransaction(tx4);

        assertEquals(TransactionStatus.PENDING_REVIEW, res4.transaction().getStatus());
        assertEquals(2, res4.triggeredRules().size(), "Both rules must be triggered");

        Set<RuleType> types = Set.of(RuleType.AMOUNT_THRESHOLD, RuleType.VELOCITY);
        assertTrue(res4.triggeredRules().stream().map(Rule::getRuleType).allMatch(types::contains));

        // Verify stored in DB
        List<FlaggedTransaction> flagged = flaggedTransactionRepository.findAll();
        FlaggedTransaction ft = flagged.get(flagged.size() - 1);
        assertEquals(2, ft.getTriggeredRules().size(), "FlaggedTransaction record must contain BOTH rules");
    }

    @Test
    @DisplayName("TEST 5: Approve a flagged transaction -> COMPLETED")
    void test5_ApproveFlaggedTransaction() {
        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal("60000.00"));
        tx.setSenderAccount("USER_X");
        tx.setReceiverAccount("USER_Y");
        tx.setTimestamp(LocalDateTime.now());

        fraudDetectionService.processTransaction(tx);
        FlaggedTransaction flagged = flaggedTransactionRepository.findAll().get(0);
        assertEquals(ReviewStatus.PENDING, flagged.getReviewStatus());
        assertEquals(TransactionStatus.PENDING_REVIEW, flagged.getTransaction().getStatus());

        fraudDetectionService.approveFlaggedTransaction(flagged.getId());

        FlaggedTransaction updatedFlagged = flaggedTransactionRepository.findById(flagged.getId()).orElseThrow();
        assertEquals(ReviewStatus.APPROVED, updatedFlagged.getReviewStatus());
        assertNotNull(updatedFlagged.getReviewedAt());

        Transaction updatedTx = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(TransactionStatus.COMPLETED, updatedTx.getStatus());
    }

    @Test
    @DisplayName("TEST 6: Block a flagged transaction -> BLOCKED")
    void test6_BlockFlaggedTransaction() {
        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal("60000.00"));
        tx.setSenderAccount("USER_M");
        tx.setReceiverAccount("USER_N");
        tx.setTimestamp(LocalDateTime.now());

        fraudDetectionService.processTransaction(tx);
        FlaggedTransaction flagged = flaggedTransactionRepository.findAll().get(0);

        fraudDetectionService.blockFlaggedTransaction(flagged.getId());

        FlaggedTransaction updatedFlagged = flaggedTransactionRepository.findById(flagged.getId()).orElseThrow();
        assertEquals(ReviewStatus.BLOCKED, updatedFlagged.getReviewStatus());
        assertNotNull(updatedFlagged.getReviewedAt());

        Transaction updatedTx = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(TransactionStatus.BLOCKED, updatedTx.getStatus());
    }

    @Test
    @DisplayName("TEST 7: Try to approve an already blocked transaction -> Operation rejected with clear message")
    void test7_TryToApproveBlockedTransaction() {
        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal("80000.00"));
        tx.setSenderAccount("USER_P");
        tx.setReceiverAccount("USER_Q");
        tx.setTimestamp(LocalDateTime.now());

        fraudDetectionService.processTransaction(tx);
        FlaggedTransaction flagged = flaggedTransactionRepository.findAll().get(0);

        // Block it first
        fraudDetectionService.blockFlaggedTransaction(flagged.getId());

        // Now attempt to approve it
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            fraudDetectionService.approveFlaggedTransaction(flagged.getId());
        });

        assertEquals("Transaction cannot be approved because it has already been blocked.", ex.getMessage());

        // Verify status remains BLOCKED
        Transaction recheckedTx = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(TransactionStatus.BLOCKED, recheckedTx.getStatus());
    }

    @Test
    @DisplayName("TEST 8: Disable a rule and create a transaction that would normally trigger it -> Ignored")
    void test8_DisabledRuleIsIgnored() {
        Rule amountRule = ruleRepository.findByEnabledTrue().stream()
                .filter(r -> r.getRuleType() == RuleType.AMOUNT_THRESHOLD)
                .findFirst().orElseThrow();

        // Disable rule
        amountRule.setEnabled(false);
        ruleRepository.save(amountRule);

        // Transaction with amount > threshold
        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal("75000.00"));
        tx.setSenderAccount("USER_DIS");
        tx.setReceiverAccount("USER_OK");
        tx.setTimestamp(LocalDateTime.now());

        FraudDetectionService.ProcessResult result = fraudDetectionService.processTransaction(tx);

        assertEquals(TransactionStatus.COMPLETED, result.transaction().getStatus());
        assertTrue(result.triggeredRules().isEmpty(), "Disabled rule must not participate in evaluation");
        assertEquals(0, flaggedTransactionRepository.count());
    }

    @Test
    @DisplayName("TEST 9: Submit invalid transaction data -> Clear validation errors")
    void test9_InvalidTransactionData() {
        // 1. Amount is null or <= 0
        Transaction txNullAmount = new Transaction();
        txNullAmount.setAmount(null);
        txNullAmount.setSenderAccount("ACC1");
        txNullAmount.setReceiverAccount("ACC2");
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> {
            fraudDetectionService.processTransaction(txNullAmount);
        });
        assertEquals("Transaction amount must be greater than 0.", ex1.getMessage());

        Transaction txZeroAmount = new Transaction();
        txZeroAmount.setAmount(BigDecimal.ZERO);
        txZeroAmount.setSenderAccount("ACC1");
        txZeroAmount.setReceiverAccount("ACC2");
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> {
            fraudDetectionService.processTransaction(txZeroAmount);
        });
        assertEquals("Transaction amount must be greater than 0.", ex2.getMessage());

        // 2. Sender blank
        Transaction txBlankSender = new Transaction();
        txBlankSender.setAmount(new BigDecimal("100"));
        txBlankSender.setSenderAccount("   ");
        txBlankSender.setReceiverAccount("ACC2");
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () -> {
            fraudDetectionService.processTransaction(txBlankSender);
        });
        assertEquals("Sender account is required.", ex3.getMessage());

        // 3. Receiver blank
        Transaction txBlankReceiver = new Transaction();
        txBlankReceiver.setAmount(new BigDecimal("100"));
        txBlankReceiver.setSenderAccount("ACC1");
        txBlankReceiver.setReceiverAccount("");
        IllegalArgumentException ex4 = assertThrows(IllegalArgumentException.class, () -> {
            fraudDetectionService.processTransaction(txBlankReceiver);
        });
        assertEquals("Receiver account is required.", ex4.getMessage());

        // 4. Sender and Receiver identical
        Transaction txSameAccounts = new Transaction();
        txSameAccounts.setAmount(new BigDecimal("100"));
        txSameAccounts.setSenderAccount("ACC_SAME");
        txSameAccounts.setReceiverAccount("acc_same");
        IllegalArgumentException ex5 = assertThrows(IllegalArgumentException.class, () -> {
            fraudDetectionService.processTransaction(txSameAccounts);
        });
        assertEquals("Sender and receiver accounts cannot be the same.", ex5.getMessage());
    }
}
