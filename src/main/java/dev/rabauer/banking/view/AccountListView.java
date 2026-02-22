package dev.rabauer.banking.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import dev.rabauer.banking.entity.Account;
import dev.rabauer.banking.service.AccountService;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Route("")
public class AccountListView extends VerticalLayout {

    private final AccountService accountService;

    private Grid<Account> grid;
    private Map<Long, BigDecimal> balanceCache = new HashMap<>();

    @Inject
    public AccountListView(AccountService accountService) {
        this.accountService = accountService;
        buildUI();
        refreshGrid();
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private void buildUI() {
        setPadding(true);
        setSpacing(true);

        add(new H1("Banking App"));
        add(new H2("Accounts"));

        Button newBtn = new Button("New Account", e -> openNewAccountDialog());
        add(newBtn);

        grid = new Grid<>(Account.class, false);
        grid.addColumn(Account::getAccountNumber).setHeader("Account Number").setSortable(true);
        grid.addColumn(Account::getOwnerName).setHeader("Owner").setSortable(true);
        grid.addColumn(a -> formatBalance(balanceCache.getOrDefault(a.getId(), BigDecimal.ZERO)))
            .setHeader("Balance");
        grid.addColumn(Account::getCreatedAt).setHeader("Created");

        // Row click → detail view
        grid.addItemClickListener(e -> {
            Account account = e.getItem();
            getUI().ifPresent(ui -> ui.navigate(
                AccountDetailView.class,
                new com.vaadin.flow.router.RouteParameters("accountId", String.valueOf(account.getId()))
            ));
        });

        add(grid);
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private void openNewAccountDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New Account");

        TextField ownerField = new TextField("Owner Name");
        ownerField.setWidthFull();
        ownerField.setRequiredIndicatorVisible(true);
        ownerField.setPlaceholder("e.g. John Doe");

        Button cancel = new Button("Cancel", e -> dialog.close());
        Button create = new Button("Create", e -> {
            String name = ownerField.getValue().trim();
            if (name.isBlank()) {
                Notification.show("Please enter an owner name")
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            accountService.createAccount(name);
            dialog.close();
            refreshGrid();
            Notification.show("Account created").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        create.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);

        dialog.add(ownerField);
        dialog.getFooter().add(cancel, create);
        dialog.open();
        ownerField.focus();
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private void refreshGrid() {
        List<Account> accounts = accountService.findAll();
        balanceCache = new HashMap<>();
        accounts.forEach(a -> balanceCache.put(a.getId(),
            accountService.calculateCurrentBalance(a.getId())));
        grid.setItems(accounts);
    }

    private String formatBalance(BigDecimal balance) {
        return balance.setScale(2, RoundingMode.HALF_UP).toPlainString() + " €";
    }
}
