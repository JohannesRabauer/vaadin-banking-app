package dev.rabauer.banking.repository;

import dev.rabauer.banking.entity.Account;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AccountRepository implements PanacheRepository<Account> {

    public Optional<Account> findByAccountNumber(String accountNumber) {
        return find("accountNumber", accountNumber).firstResultOptional();
    }

    public List<Account> findAllOrderedByOwner() {
        return list("ORDER BY ownerName ASC");
    }
}
