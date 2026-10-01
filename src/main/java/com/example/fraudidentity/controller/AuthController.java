package com.example.fraudidentity.controller;

import com.example.fraudidentity.entity.AccountHolder;
import com.example.fraudidentity.service.AccountHolderService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Optional;

@Controller
public class AuthController {

    private final AccountHolderService accountHolderService;

    public AuthController(AccountHolderService accountHolderService) {
        this.accountHolderService = accountHolderService;
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        // If already logged in, redirect to respective dashboard
        if (session.getAttribute("currentAccount") != null) {
            return "redirect:/account/dashboard";
        }
        if (Boolean.TRUE.equals(session.getAttribute("isAdmin"))) {
            return "redirect:/admin/dashboard";
        }
        return "index";
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        AccountHolder accountHolder = new AccountHolder();
        accountHolder.setBalance(new BigDecimal("10000.00"));
        model.addAttribute("accountHolder", accountHolder);
        return "register";
    }

    @PostMapping("/register")
    public String registerAccount(@Valid @ModelAttribute("accountHolder") AccountHolder accountHolder,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            accountHolderService.registerAccount(accountHolder);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Bank Account '" + accountHolder.getAccountNumber() + "' created successfully! Please login below.");
            return "redirect:/login/account";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "register";
        }
    }

    @GetMapping("/login/account")
    public String showAccountLoginForm() {
        return "login-account";
    }

    @PostMapping("/login/account")
    public String processAccountLogin(@RequestParam("accountNumber") String accountNumber,
                                      @RequestParam("password") String password,
                                      HttpSession session,
                                      Model model) {
        Optional<AccountHolder> optAccount = accountHolderService.authenticate(accountNumber, password);
        if (optAccount.isPresent()) {
            session.setAttribute("currentAccount", optAccount.get().getAccountNumber());
            session.removeAttribute("isAdmin");
            return "redirect:/account/dashboard";
        } else {
            model.addAttribute("errorMessage", "Invalid Account Number or Password.");
            return "login-account";
        }
    }

    @GetMapping("/login/admin")
    public String showAdminLoginForm() {
        return "login-admin";
    }

    @PostMapping("/login/admin")
    public String processAdminLogin(@RequestParam("username") String username,
                                    @RequestParam("password") String password,
                                    HttpSession session,
                                    Model model) {
        if ("admin".equalsIgnoreCase(username != null ? username.trim() : "")
                && ("admin".equals(password) || "admin123".equals(password))) {
            session.setAttribute("isAdmin", true);
            session.removeAttribute("currentAccount");
            return "redirect:/admin/dashboard";
        } else {
            model.addAttribute("errorMessage", "Invalid Admin username or password. (Hint: admin / admin)");
            return "login-admin";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.invalidate();
        redirectAttributes.addFlashAttribute("successMessage", "You have been logged out successfully.");
        return "redirect:/";
    }
}
