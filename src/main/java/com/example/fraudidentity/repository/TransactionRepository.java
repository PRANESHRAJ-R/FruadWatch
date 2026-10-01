package com.example.fraudidentity.repository;

import com.example.fraudidentity.entity.Transaction;
import com.example.fraudidentity.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    long countBySenderAccountAndTimestampBetween(String senderAccount, LocalDateTime start, LocalDateTime end);

    long countByStatus(TransactionStatus status);

    List<Transaction> findAllByOrderByTimestampDesc();

    List<Transaction> findBySenderAccountOrReceiverAccountOrderByTimestampDesc(String senderAccount, String receiverAccount);
}
