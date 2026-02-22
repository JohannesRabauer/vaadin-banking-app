package dev.rabauer.banking.service;

import dev.rabauer.banking.entity.Account;
import dev.rabauer.banking.repository.AccountRepository;
import dev.rabauer.banking.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class AccountService {

    @Inject
    AccountRepository accountRepository;

    @Inject
    TransactionRepository transactionRepository;

    @Transactional
    public Account createAccount(String ownerName) {
        Account account = new Account();
        account.setOwnerName(ownerName.trim());
        account.setAccountNumber(generateAccountNumber());
        accountRepository.persist(account);
        return account;
    }

    @Transactional
    public BigDecimal calculateCurrentBalance(Long accountId) {
        return transactionRepository.calculateBalance(accountId);
    }

    @Transactional
    public List<Account> findAll() {
        return accountRepository.findAllOrderedByOwner();
    }

    @Transactional
    public Optional<Account> findById(Long id) {
        return Optional.ofNullable(accountRepository.findById(id));
    }

    // --- Private helpers ---

    private String generateAccountNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        String candidate = "DE" + timestamp + random;
        // Ensure uniqueness (retry on collision)
        while (accountRepository.findByAccountNumber(candidate).isPresent()) {
            random = ThreadLocalRandom.current().nextInt(1000, 9999);
            candidate = "DE" + timestamp + random;
        }
        return candidate;
    }
}
