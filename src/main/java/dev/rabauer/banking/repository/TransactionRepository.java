package dev.rabauer.banking.repository;

import dev.rabauer.banking.entity.Account;
import dev.rabauer.banking.entity.Transaction;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;

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
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Transaction> cq = cb.createQuery(Transaction.class);
        Root<Transaction> root = cq.from(Transaction.class);
        root.fetch("targetAccount", JoinType.LEFT);
        cq.select(root)
          .where(cb.equal(root.get("account").get("id"), accountId))
          .orderBy(cb.desc(root.get("createdAt")));
        return em.createQuery(cq).getResultList();
    }

    public List<Transaction> findByAccount(Account account) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Transaction> cq = cb.createQuery(Transaction.class);
        Root<Transaction> root = cq.from(Transaction.class);
        root.fetch("targetAccount", JoinType.LEFT);
        cq.select(root)
          .where(cb.equal(root.get("account"), account))
          .orderBy(cb.desc(root.get("createdAt")));
        return em.createQuery(cq).getResultList();
    }

    /**
     * Calculates the current balance for an account by summing all signed transaction amounts.
     * Positive amounts = deposits/incoming transfers.
     * Negative amounts = withdrawals/outgoing transfers.
     *
     * @return current balance, BigDecimal.ZERO if no transactions exist
     */
    public BigDecimal calculateBalance(Long accountId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> cq = cb.createQuery(BigDecimal.class);
        Root<Transaction> root = cq.from(Transaction.class);
        cq.select(cb.sum(root.get("amount")))
          .where(cb.equal(root.get("account").get("id"), accountId));
        BigDecimal result = em.createQuery(cq).getSingleResult();
        return result != null ? result : BigDecimal.ZERO;
    }
}
