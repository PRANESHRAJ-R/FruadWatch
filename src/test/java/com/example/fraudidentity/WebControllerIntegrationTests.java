package com.example.fraudidentity;

import com.example.fraudidentity.entity.AccountHolder;
import com.example.fraudidentity.entity.Rule;
import com.example.fraudidentity.entity.Transaction;
import com.example.fraudidentity.enums.RuleType;
import com.example.fraudidentity.repository.AccountHolderRepository;
import com.example.fraudidentity.repository.FlaggedTransactionRepository;
import com.example.fraudidentity.repository.RuleRepository;
import com.example.fraudidentity.repository.TransactionRepository;
import com.example.fraudidentity.service.AccountHolderService;
import com.example.fraudidentity.service.FraudDetectionService;
import com.example.fraudidentity.service.RuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class WebControllerIntegrationTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private FlaggedTransactionRepository flaggedTransactionRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @Autowired
    private RuleService ruleService;

    @Autowired
    private AccountHolderService accountHolderService;

    @Autowired
    private FraudDetectionService fraudDetectionService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        flaggedTransactionRepository.deleteAll();
        transactionRepository.deleteAll();
        ruleRepository.deleteAll();
        accountHolderRepository.deleteAll();
        ruleService.initDefaultRules();
        accountHolderService.initSampleAccounts();
    }

    @Test
    @DisplayName("GET / renders home role landing view and /dashboard renders dashboard view")
    void testLandingAndDashboardView() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("stats"))
                .andExpect(model().attributeExists("recentFlagged"));
    }

    @Test
    @DisplayName("GET /register and POST /register creates a bank account holder")
    void testRegisterAccountHolder() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("accountHolder"));

        mockMvc.perform(post("/register")
                        .param("accountNumber", "ACC9999")
                        .param("fullName", "Test User")
                        .param("email", "testuser@example.com")
                        .param("password", "pin123")
                        .param("balance", "25000.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login/account"))
                .andExpect(flash().attributeExists("successMessage"));

        org.junit.jupiter.api.Assertions.assertTrue(accountHolderRepository.existsByAccountNumber("ACC9999"));
    }

    @Test
    @DisplayName("POST /login/account authenticates account holder")
    void testAccountHolderLogin() throws Exception {
        mockMvc.perform(post("/login/account")
                        .param("accountNumber", "ACC1001")
                        .param("password", "123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/dashboard"));
    }

    @Test
    @DisplayName("POST /login/admin authenticates admin")
    void testAdminLogin() throws Exception {
        mockMvc.perform(post("/login/admin")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    @DisplayName("GET /admin/accounts renders accounts view for admin")
    void testAdminViewAccounts() throws Exception {
        mockMvc.perform(get("/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-accounts"))
                .andExpect(model().attributeExists("accounts"));
    }

    @Test
    @DisplayName("POST /account/transfer executes money transfer for logged in user")
    void testAccountTransfer() throws Exception {
        mockMvc.perform(post("/account/transfer")
                        .sessionAttr("currentAccount", "ACC1001")
                        .param("receiverAccount", "ACC1002")
                        .param("amount", "1500.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/dashboard"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("GET /transactions renders transactions view")
    void testTransactionsListView() throws Exception {
        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions"))
                .andExpect(model().attributeExists("transactions"));
    }

    @Test
    @DisplayName("GET /transactions/new renders add-transaction form")
    void testAddTransactionFormView() throws Exception {
        mockMvc.perform(get("/transactions/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("add-transaction"))
                .andExpect(model().attributeExists("transaction"));
    }

    @Test
    @DisplayName("POST /transactions with valid data creates transaction and redirects")
    void testCreateTransactionSuccess() throws Exception {
        mockMvc.perform(post("/transactions")
                        .param("amount", "250.00")
                        .param("senderAccount", "ACC_USER1")
                        .param("receiverAccount", "ACC_USER2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("POST /transactions with same sender and receiver rejects with form error")
    void testCreateTransactionSameSenderReceiverError() throws Exception {
        mockMvc.perform(post("/transactions")
                        .param("amount", "250.00")
                        .param("senderAccount", "SAME_ACCOUNT")
                        .param("receiverAccount", "SAME_ACCOUNT"))
                .andExpect(status().isOk())
                .andExpect(view().name("add-transaction"))
                .andExpect(model().hasErrors());
    }

    @Test
    @DisplayName("POST /transactions with amount > 50000 flags and redirects with warning")
    void testCreateTransactionAboveThresholdFlags() throws Exception {
        mockMvc.perform(post("/transactions")
                        .param("amount", "75000.00")
                        .param("senderAccount", "BIG_SPENDER")
                        .param("receiverAccount", "VENDOR_1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"))
                .andExpect(flash().attributeExists("warningMessage"));
    }

    @Test
    @DisplayName("GET /flagged renders flagged transactions review queue")
    void testFlaggedTransactionsView() throws Exception {
        mockMvc.perform(get("/flagged"))
                .andExpect(status().isOk())
                .andExpect(view().name("flagged-transactions"))
                .andExpect(model().attributeExists("flaggedList"));
    }

    @Test
    @DisplayName("POST /flagged/{id}/approve approves flagged transaction")
    void testApproveFlaggedTransaction() throws Exception {
        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal("99000.00"));
        tx.setSenderAccount("SENDER_FLAG");
        tx.setReceiverAccount("RECEIVER_FLAG");
        tx.setTimestamp(LocalDateTime.now());
        fraudDetectionService.processTransaction(tx);

        var flagged = flaggedTransactionRepository.findAll().get(0);

        mockMvc.perform(post("/flagged/" + flagged.getId() + "/approve"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flagged"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("POST /flagged/{id}/block reports flagged transaction as fraud and blocks it")
    void testBlockFlaggedTransaction() throws Exception {
        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal("99000.00"));
        tx.setSenderAccount("SENDER_BLOCK");
        tx.setReceiverAccount("RECEIVER_BLOCK");
        tx.setTimestamp(LocalDateTime.now());
        fraudDetectionService.processTransaction(tx);

        var flagged = flaggedTransactionRepository.findAll().get(0);

        mockMvc.perform(post("/flagged/" + flagged.getId() + "/block"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flagged"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("GET /rules renders rules management view")
    void testRulesView() throws Exception {
        mockMvc.perform(get("/rules"))
                .andExpect(status().isOk())
                .andExpect(view().name("rules"))
                .andExpect(model().attributeExists("rules"));
    }

    @Test
    @DisplayName("POST /rules/new adds a new rule and redirects")
    void testCreateNewRule() throws Exception {
        mockMvc.perform(post("/rules")
                        .param("name", "Custom Threshold Rule")
                        .param("ruleType", "AMOUNT_THRESHOLD")
                        .param("thresholdAmount", "100000.00")
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rules"))
                .andExpect(flash().attribute("successMessage", "Rule created successfully."));
    }

    @Test
    @DisplayName("POST /rules/{id}/toggle toggles enabled status")
    void testToggleRule() throws Exception {
        Rule rule = ruleRepository.findAll().get(0);
        boolean initial = rule.isEnabled();

        mockMvc.perform(post("/rules/" + rule.getId() + "/toggle"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rules"))
                .andExpect(flash().attributeExists("successMessage"));

        Rule rechecked = ruleRepository.findById(rule.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals(initial, rechecked.isEnabled());
    }
}
