package dev.rabauer.banking.service;

import dev.rabauer.banking.entity.Account;
import dev.rabauer.banking.entity.Transaction;
import dev.rabauer.banking.entity.TransactionType;
import dev.rabauer.banking.repository.AccountRepository;
import dev.rabauer.banking.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@ApplicationScoped
public class TransactionService {

    @Inject
    AccountRepository accountRepository;

    @Inject
    TransactionRepository transactionRepository;

    /**
     * Deposits cash into an account.
     * Creates a Transaction with a positive amount and no target account.
     */
    @Transactional
    public Transaction deposit(Long accountId, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        Account account = lockAccount(accountId);
        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setType(TransactionType.DEPOSIT);
        tx.setAmount(scale(amount));
        tx.setDescription(description);
        transactionRepository.persist(tx);
        return tx;
    }

    /**
     * Withdraws cash from an account.
     * Creates a Transaction with a negative amount and no target account.
     * Throws InsufficientFundsException if the current balance is too low.
     */
    @Transactional
    public Transaction withdraw(Long accountId, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        Account account = lockAccount(accountId);
        BigDecimal currentBalance = transactionRepository.calculateBalance(accountId);
        if (currentBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                "Insufficient funds: balance is " + currentBalance + ", requested " + amount);
        }
        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setType(TransactionType.WITHDRAWAL);
        tx.setAmount(scale(amount).negate());
        tx.setDescription(description);
        transactionRepository.persist(tx);
        return tx;
    }

    /**
     * Transfers money between two accounts.
     * Creates two Transaction records atomically:
     *   - Source account: negative amount, targetAccount set to destination.
     *   - Target account: positive amount, targetAccount set to source (for reference).
     * Locks are always acquired in ascending id order to avoid deadlocks.
     */
    @Transactional
    public void transfer(Long sourceAccountId, Long targetAccountId,
                         BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if (sourceAccountId.equals(targetAccountId)) {
            throw new IllegalArgumentException("Source and target accounts must differ");
        }
        // Lock in ascending id order to prevent deadlocks
        Long firstId  = Math.min(sourceAccountId, targetAccountId);
        Long secondId = Math.max(sourceAccountId, targetAccountId);
        Account first  = lockAccount(firstId);
        Account second = lockAccount(secondId);

        Account source = first.getId().equals(sourceAccountId) ? first : second;
        Account target = first.getId().equals(targetAccountId) ? first : second;

        BigDecimal currentBalance = transactionRepository.calculateBalance(sourceAccountId);
        if (currentBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                "Insufficient funds: balance is " + currentBalance + ", requested " + amount);
        }

        BigDecimal scaled = scale(amount);

        // Debit source
        Transaction debit = new Transaction();
        debit.setAccount(source);
        debit.setTargetAccount(target);
        debit.setType(TransactionType.TRANSFER);
        debit.setAmount(scaled.negate());
        debit.setDescription(description);
        transactionRepository.persist(debit);

        // Credit target
        Transaction credit = new Transaction();
        credit.setAccount(target);
        credit.setTargetAccount(source);
        credit.setType(TransactionType.TRANSFER);
        credit.setAmount(scaled);
        credit.setDescription(description);
        transactionRepository.persist(credit);
    }

    @Transactional
    public List<Transaction> getTransactionHistory(Long accountId) {
        return transactionRepository.findByAccountId(accountId);
    }

    // --- Private helpers ---

    private Account lockAccount(Long accountId) {
        Account account = accountRepository.getEntityManager()
            .find(Account.class, accountId, LockModeType.PESSIMISTIC_WRITE);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return account;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
