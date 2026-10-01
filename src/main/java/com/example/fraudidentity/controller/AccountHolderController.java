package com.example.fraudidentity.controller;

import com.example.fraudidentity.entity.AccountHolder;
import com.example.fraudidentity.entity.Transaction;
import com.example.fraudidentity.service.AccountHolderService;
import com.example.fraudidentity.service.FraudDetectionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/account")
public class AccountHolderController {

    private final AccountHolderService accountHolderService;
    private final FraudDetectionService fraudDetectionService;

    public AccountHolderController(AccountHolderService accountHolderService,
                                   FraudDetectionService fraudDetectionService) {
        this.accountHolderService = accountHolderService;
        this.fraudDetectionService = fraudDetectionService;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        String accNo = (String) session.getAttribute("currentAccount");
        if (accNo == null) {
            return "redirect:/login/account";
        }

        Optional<AccountHolder> optAccount = accountHolderService.findByAccountNumber(accNo);
        if (optAccount.isEmpty()) {
            session.invalidate();
            return "redirect:/login/account";
        }

        AccountHolder currentAccount = optAccount.get();
        List<AccountHolder> otherAccounts = accountHolderService.getAllAccountHolders().stream()
                .filter(a -> !a.getAccountNumber().equalsIgnoreCase(accNo))
                .toList();
        List<Transaction> transactions = accountHolderService.getTransactionsForAccount(accNo);

        model.addAttribute("account", currentAccount);
        model.addAttribute("otherAccounts", otherAccounts);
        model.addAttribute("transactions", transactions);
        return "account-dashboard";
    }

    @PostMapping("/transfer")
    public String executeTransfer(@RequestParam("receiverAccount") String receiverAccount,
                                  @RequestParam("amount") BigDecimal amount,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        String senderAccNo = (String) session.getAttribute("currentAccount");
        if (senderAccNo == null) {
            return "redirect:/login/account";
        }

        AccountHolder sender = accountHolderService.findByAccountNumber(senderAccNo)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "Transfer amount must be greater than 0.");
            return "redirect:/account/dashboard";
        }

        if (receiverAccount == null || receiverAccount.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Receiver account number is required.");
            return "redirect:/account/dashboard";
        }

        if (senderAccNo.trim().equalsIgnoreCase(receiverAccount.trim())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Sender and receiver accounts cannot be the same.");
            return "redirect:/account/dashboard";
        }

        if (sender.getBalance().compareTo(amount) < 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Insufficient balance. Your available balance is " + sender.getBalance() + ".");
            return "redirect:/account/dashboard";
        }

        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setSenderAccount(senderAccNo.trim());
        transaction.setReceiverAccount(receiverAccount.trim());
        transaction.setTimestamp(LocalDateTime.now());

        try {
            FraudDetectionService.ProcessResult result = fraudDetectionService.processTransaction(transaction);
            if (result.triggeredRules().isEmpty()) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Transaction #" + result.transaction().getId() + " of $" + amount + " completed successfully!");
            } else {
                redirectAttributes.addFlashAttribute("warningMessage",
                        "Transaction #" + result.transaction().getId() + " of $" + amount
                                + " was flagged as suspicious (" + result.triggeredRules().size()
                                + " fraud rule(s) triggered) and is held in PENDING REVIEW for Bank Admin approval.");
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/account/dashboard";
    }
}
