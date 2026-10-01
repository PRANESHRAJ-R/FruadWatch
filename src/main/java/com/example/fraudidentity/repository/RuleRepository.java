package com.example.fraudidentity.repository;

import com.example.fraudidentity.entity.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {

    List<Rule> findByEnabledTrue();

    boolean existsByName(String name);

    List<Rule> findAllByOrderByIdAsc();
}
