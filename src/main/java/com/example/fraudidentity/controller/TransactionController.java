package com.example.fraudidentity.controller;

import com.example.fraudidentity.entity.Transaction;
import com.example.fraudidentity.service.AccountHolderService;
import com.example.fraudidentity.service.FraudDetectionService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Controller
public class TransactionController {

    private final FraudDetectionService fraudDetectionService;
    private final AccountHolderService accountHolderService;

    public TransactionController(FraudDetectionService fraudDetectionService,
                                 AccountHolderService accountHolderService) {
        this.fraudDetectionService = fraudDetectionService;
        this.accountHolderService = accountHolderService;
    }

    @GetMapping({"/dashboard", "/admin/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("stats", fraudDetectionService.getDashboardStats());
        model.addAttribute("recentFlagged", fraudDetectionService.getRecentFlaggedTransactions());
        return "dashboard";
    }

    @GetMapping("/admin/accounts")
    public String listAccountHolders(Model model) {
        model.addAttribute("accounts", accountHolderService.getAllAccountHolders());
        return "admin-accounts";
    }

    @GetMapping({"/transactions", "/admin/transactions"})
    public String listTransactions(Model model) {
        model.addAttribute("transactions", fraudDetectionService.getAllTransactions());
        return "transactions";
    }

    @GetMapping("/transactions/new")
    public String showCreateTransactionForm(Model model) {
        Transaction transaction = new Transaction();
        transaction.setTimestamp(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        model.addAttribute("transaction", transaction);
        return "add-transaction";
    }

    @PostMapping("/transactions")
    public String createTransaction(@Valid @ModelAttribute("transaction") Transaction transaction,
                                    BindingResult bindingResult,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        if (transaction.getSenderAccount() != null && transaction.getReceiverAccount() != null
                && !transaction.getSenderAccount().trim().isEmpty()
                && transaction.getSenderAccount().trim().equalsIgnoreCase(transaction.getReceiverAccount().trim())) {
            bindingResult.rejectValue("receiverAccount", "error.receiverAccount", "Sender and receiver accounts cannot be the same.");
        }

        if (bindingResult.hasErrors()) {
            return "add-transaction";
        }

        try {
            FraudDetectionService.ProcessResult result = fraudDetectionService.processTransaction(transaction);
            if (result.triggeredRules().isEmpty()) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Transaction #" + result.transaction().getId() + " created successfully.");
            } else {
                redirectAttributes.addFlashAttribute("warningMessage",
                        "Transaction #" + result.transaction().getId() + " flagged for manual review because "
                                + result.triggeredRules().size() + " fraud rule(s) were triggered.");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "add-transaction";
        }

        return "redirect:/transactions";
    }

    @GetMapping({"/flagged", "/admin/flagged", "/flagged-transactions"})
    public String listFlaggedTransactions(Model model) {
        model.addAttribute("flaggedList", fraudDetectionService.getAllFlaggedTransactions());
        return "flagged-transactions";
    }

    @PostMapping("/flagged/{id}/approve")
    public String approveTransaction(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        fraudDetectionService.approveFlaggedTransaction(id);
        redirectAttributes.addFlashAttribute("successMessage", "Transaction #" + id + " has been APPROVED and COMPLETED.");
        return "redirect:/flagged";
    }

    @PostMapping("/flagged/{id}/block")
    public String blockTransaction(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        fraudDetectionService.blockFlaggedTransaction(id);
        redirectAttributes.addFlashAttribute("successMessage", "Transaction #" + id + " reported as FRAUD and BLOCKED.");
        return "redirect:/flagged";
    }
}
