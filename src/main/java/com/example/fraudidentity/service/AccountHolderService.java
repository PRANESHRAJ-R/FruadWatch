package com.example.fraudidentity.service;

import com.example.fraudidentity.entity.AccountHolder;
import com.example.fraudidentity.entity.Transaction;
import com.example.fraudidentity.repository.AccountHolderRepository;
import com.example.fraudidentity.repository.TransactionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class AccountHolderService {

    private final AccountHolderRepository accountHolderRepository;
    private final TransactionRepository transactionRepository;

    public AccountHolderService(AccountHolderRepository accountHolderRepository,
                                TransactionRepository transactionRepository) {
        this.accountHolderRepository = accountHolderRepository;
        this.transactionRepository = transactionRepository;
    }

    @PostConstruct
    public void initSampleAccounts() {
        if (accountHolderRepository.count() == 0) {
            createSampleAccount("ACC1001", "Pranesh Raj", "pranesh@example.com", "123", new BigDecimal("100000.00"));
            createSampleAccount("ACC1002", "Priya Sharma", "priya@example.com", "123", new BigDecimal("50000.00"));
            createSampleAccount("ACC1003", "Rahul Kumar", "rahul@example.com", "123", new BigDecimal("75000.00"));
        }
    }

    private void createSampleAccount(String accNo, String name, String email, String pwd, BigDecimal bal) {
        if (!accountHolderRepository.existsByAccountNumber(accNo)) {
            AccountHolder account = new AccountHolder(accNo, name, email, pwd, bal);
            accountHolderRepository.save(account);
        }
    }

    public List<AccountHolder> getAllAccountHolders() {
        return accountHolderRepository.findAllByOrderByCreatedAtDesc();
    }

    public long countAccountHolders() {
        return accountHolderRepository.count();
    }

    public Optional<AccountHolder> findByAccountNumber(String accountNumber) {
        if (accountNumber == null) return Optional.empty();
        return accountHolderRepository.findByAccountNumber(accountNumber.trim());
    }

    public Optional<AccountHolder> authenticate(String accountNumber, String password) {
        if (accountNumber == null || password == null) return Optional.empty();
        return accountHolderRepository.findByAccountNumber(accountNumber.trim())
                .filter(acc -> acc.getPassword().equals(password.trim()));
    }

    @Transactional
    public AccountHolder registerAccount(AccountHolder accountHolder) {
        if (accountHolder.getAccountNumber() == null || accountHolder.getAccountNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Account number is required.");
        }
        String cleanAccNo = accountHolder.getAccountNumber().trim();
        if (accountHolderRepository.existsByAccountNumber(cleanAccNo)) {
            throw new IllegalArgumentException("Account number '" + cleanAccNo + "' already exists. Please choose a different account number.");
        }
        if (accountHolder.getFullName() == null || accountHolder.getFullName().trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (accountHolder.getEmail() == null || accountHolder.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (accountHolder.getPassword() == null || accountHolder.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (accountHolder.getBalance() == null || accountHolder.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative.");
        }

        accountHolder.setAccountNumber(cleanAccNo);
        accountHolder.setFullName(accountHolder.getFullName().trim());
        accountHolder.setEmail(accountHolder.getEmail().trim());
        accountHolder.setPassword(accountHolder.getPassword().trim());

        return accountHolderRepository.save(accountHolder);
    }

    public List<Transaction> getTransactionsForAccount(String accountNumber) {
        if (accountNumber == null) return List.of();
        String acc = accountNumber.trim();
        return transactionRepository.findBySenderAccountOrReceiverAccountOrderByTimestampDesc(acc, acc);
    }

    @Transactional
    public void deductBalanceIfPossible(String accountNumber, BigDecimal amount) {
        accountHolderRepository.findByAccountNumber(accountNumber.trim()).ifPresent(acc -> {
            if (acc.getBalance().compareTo(amount) >= 0) {
                acc.setBalance(acc.getBalance().subtract(amount));
                accountHolderRepository.save(acc);
            }
        });
    }

    @Transactional
    public void creditBalance(String accountNumber, BigDecimal amount) {
        accountHolderRepository.findByAccountNumber(accountNumber.trim()).ifPresent(acc -> {
            acc.setBalance(acc.getBalance().add(amount));
            accountHolderRepository.save(acc);
        });
    }
}
