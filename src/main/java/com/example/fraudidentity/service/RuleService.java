package com.example.fraudidentity.service;

import com.example.fraudidentity.entity.Rule;
import com.example.fraudidentity.enums.RuleType;
import com.example.fraudidentity.repository.RuleRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RuleService {

    private final RuleRepository ruleRepository;

    public RuleService(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @PostConstruct
    public void initDefaultRules() {
        if (!ruleRepository.existsByName("Large Amount Rule")) {
            Rule amountRule = new Rule();
            amountRule.setName("Large Amount Rule");
            amountRule.setRuleType(RuleType.AMOUNT_THRESHOLD);
            amountRule.setThresholdAmount(new BigDecimal("50000"));
            amountRule.setEnabled(true);
            ruleRepository.save(amountRule);
        }

        if (!ruleRepository.existsByName("Rapid Transactions Rule")) {
            Rule velocityRule = new Rule();
            velocityRule.setName("Rapid Transactions Rule");
            velocityRule.setRuleType(RuleType.VELOCITY);
            velocityRule.setTransactionCount(3);
            velocityRule.setTimeWindowMinutes(10);
            velocityRule.setEnabled(true);
            ruleRepository.save(velocityRule);
        }
    }

    public List<Rule> getAllRules() {
        return ruleRepository.findAllByOrderByIdAsc();
    }

    public Rule getRuleById(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found with id: " + id));
    }

    @Transactional
    public Rule createRule(Rule rule) {
        validateRule(rule);
        return ruleRepository.save(rule);
    }

    @Transactional
    public Rule updateRule(Long id, Rule updatedData) {
        Rule existing = getRuleById(id);
        validateRule(updatedData);

        existing.setName(updatedData.getName().trim());
        existing.setRuleType(updatedData.getRuleType());
        existing.setEnabled(updatedData.isEnabled());

        if (updatedData.getRuleType() == RuleType.AMOUNT_THRESHOLD) {
            existing.setThresholdAmount(updatedData.getThresholdAmount());
            existing.setTransactionCount(null);
            existing.setTimeWindowMinutes(null);
        } else if (updatedData.getRuleType() == RuleType.VELOCITY) {
            existing.setThresholdAmount(null);
            existing.setTransactionCount(updatedData.getTransactionCount());
            existing.setTimeWindowMinutes(updatedData.getTimeWindowMinutes());
        }

        return ruleRepository.save(existing);
    }

    @Transactional
    public Rule toggleRuleStatus(Long id) {
        Rule rule = getRuleById(id);
        rule.setEnabled(!rule.isEnabled());
        return ruleRepository.save(rule);
    }

    private void validateRule(Rule rule) {
        if (rule.getName() == null || rule.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Rule name must not be blank.");
        }
        if (rule.getRuleType() == null) {
            throw new IllegalArgumentException("Rule type is required.");
        }
        if (rule.getRuleType() == RuleType.AMOUNT_THRESHOLD) {
            if (rule.getThresholdAmount() == null || rule.getThresholdAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Threshold amount must be greater than 0.");
            }
        } else if (rule.getRuleType() == RuleType.VELOCITY) {
            if (rule.getTransactionCount() == null || rule.getTransactionCount() <= 0) {
                throw new IllegalArgumentException("Transaction count must be greater than 0.");
            }
            if (rule.getTimeWindowMinutes() == null || rule.getTimeWindowMinutes() <= 0) {
                throw new IllegalArgumentException("Time window must be greater than 0.");
            }
        }
    }
}
