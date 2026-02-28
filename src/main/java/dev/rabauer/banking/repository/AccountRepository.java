package dev.rabauer.banking.repository;

import dev.rabauer.banking.entity.Account;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AccountRepository implements PanacheRepository<Account> {

    @Inject
    EntityManager em;

    public Optional<Account> findByAccountNumber(String accountNumber) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Account> cq = cb.createQuery(Account.class);
        Root<Account> root = cq.from(Account.class);
        cq.select(root)
          .where(cb.equal(root.get("accountNumber"), accountNumber));
        List<Account> results = em.createQuery(cq).setMaxResults(1).getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Account> findAllOrderedByOwner() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Account> cq = cb.createQuery(Account.class);
        Root<Account> root = cq.from(Account.class);
        cq.select(root)
          .orderBy(cb.asc(root.get("ownerName")));
        return em.createQuery(cq).getResultList();
    }
}
