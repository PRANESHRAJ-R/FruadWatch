package com.example.fraudidentity.controller;

import com.example.fraudidentity.entity.Rule;
import com.example.fraudidentity.enums.RuleType;
import com.example.fraudidentity.service.RuleService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public String listRules(Model model) {
        model.addAttribute("rules", ruleService.getAllRules());
        return "rules";
    }

    @GetMapping("/new")
    public String showAddRuleForm(Model model) {
        Rule rule = new Rule();
        rule.setRuleType(RuleType.AMOUNT_THRESHOLD);
        rule.setEnabled(true);
        model.addAttribute("rule", rule);
        return "add-rule";
    }

    @PostMapping
    public String createRule(@Valid @ModelAttribute("rule") Rule rule,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        validateRuleForm(rule, bindingResult);

        if (bindingResult.hasErrors()) {
            return "add-rule";
        }

        try {
            ruleService.createRule(rule);
            redirectAttributes.addFlashAttribute("successMessage", "Rule created successfully.");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "add-rule";
        }

        return "redirect:/rules";
    }

    @GetMapping("/{id}/edit")
    public String showEditRuleForm(@PathVariable("id") Long id, Model model) {
        Rule rule = ruleService.getRuleById(id);
        model.addAttribute("rule", rule);
        return "edit-rule";
    }

    @PostMapping("/{id}/edit")
    public String updateRule(@PathVariable("id") Long id,
                             @Valid @ModelAttribute("rule") Rule rule,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        validateRuleForm(rule, bindingResult);

        if (bindingResult.hasErrors()) {
            return "edit-rule";
        }

        try {
            ruleService.updateRule(id, rule);
            redirectAttributes.addFlashAttribute("successMessage", "Rule updated successfully.");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "edit-rule";
        }

        return "redirect:/rules";
    }

    @PostMapping("/{id}/toggle")
    public String toggleRuleStatus(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Rule updated = ruleService.toggleRuleStatus(id);
        String statusText = updated.isEnabled() ? "enabled" : "disabled";
        redirectAttributes.addFlashAttribute("successMessage", "Rule \"" + updated.getName() + "\" has been " + statusText + ".");
        return "redirect:/rules";
    }

    private void validateRuleForm(Rule rule, BindingResult bindingResult) {
        if (rule.getName() == null || rule.getName().trim().isEmpty()) {
            bindingResult.rejectValue("name", "error.name", "Rule name must not be blank.");
        }
        if (rule.getRuleType() == null) {
            bindingResult.rejectValue("ruleType", "error.ruleType", "Rule type is required.");
            return;
        }

        if (rule.getRuleType() == RuleType.AMOUNT_THRESHOLD) {
            if (rule.getThresholdAmount() == null || rule.getThresholdAmount().compareTo(BigDecimal.ZERO) <= 0) {
                bindingResult.rejectValue("thresholdAmount", "error.thresholdAmount", "Threshold amount must be greater than 0.");
            }
        } else if (rule.getRuleType() == RuleType.VELOCITY) {
            if (rule.getTransactionCount() == null || rule.getTransactionCount() <= 0) {
                bindingResult.rejectValue("transactionCount", "error.transactionCount", "Transaction count must be greater than 0.");
            }
            if (rule.getTimeWindowMinutes() == null || rule.getTimeWindowMinutes() <= 0) {
                bindingResult.rejectValue("timeWindowMinutes", "error.timeWindowMinutes", "Time window must be greater than 0.");
            }
        }
    }
}
