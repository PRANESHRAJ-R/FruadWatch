package com.example.fraudidentity.repository;

import com.example.fraudidentity.entity.FlaggedTransaction;
import com.example.fraudidentity.enums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlaggedTransactionRepository extends JpaRepository<FlaggedTransaction, Long> {

    long countByReviewStatus(ReviewStatus reviewStatus);

    List<FlaggedTransaction> findAllByOrderByFlaggedAtDesc();

    List<FlaggedTransaction> findTop10ByOrderByFlaggedAtDesc();
}
