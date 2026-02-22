package dev.rabauer.banking.repository;

import dev.rabauer.banking.entity.Account;
import dev.rabauer.banking.entity.Transaction;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class TransactionRepository implements PanacheRepository<Transaction> {

    @Inject
    EntityManager em;

    /**
     * Fetches all transactions for an account, eagerly loading targetAccount
     * to prevent LazyInitializationException after the session closes.
     */
    public List<Transaction> findByAccountId(Long accountId) {
        return em.createQuery(
                "SELECT t FROM Transaction t " +
                "LEFT JOIN FETCH t.targetAccount " +
                "WHERE t.account.id = :id " +
                "ORDER BY t.createdAt DESC",
                Transaction.class)
            .setParameter("id", accountId)
            .getResultList();
    }

    public List<Transaction> findByAccount(Account account) {
        return em.createQuery(
                "SELECT t FROM Transaction t " +
                "LEFT JOIN FETCH t.targetAccount " +
                "WHERE t.account = :account " +
                "ORDER BY t.createdAt DESC",
                Transaction.class)
            .setParameter("account", account)
            .getResultList();
    }

    /**
     * Calculates the current balance for an account by summing all signed transaction amounts.
     * Positive amounts = deposits/incoming transfers.
     * Negative amounts = withdrawals/outgoing transfers.
     *
     * @return current balance, BigDecimal.ZERO if no transactions exist
     */
    public BigDecimal calculateBalance(Long accountId) {
        BigDecimal result = em.createQuery(
                "SELECT SUM(t.amount) FROM Transaction t WHERE t.account.id = :id",
                BigDecimal.class)
            .setParameter("id", accountId)
            .getSingleResult();
        return result != null ? result : BigDecimal.ZERO;
    }
}
