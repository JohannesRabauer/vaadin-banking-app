package dev.rabauer.banking.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import dev.rabauer.banking.entity.Account;
import dev.rabauer.banking.entity.Transaction;
import dev.rabauer.banking.entity.TransactionType;
import dev.rabauer.banking.service.AccountService;
import dev.rabauer.banking.service.InsufficientFundsException;
import dev.rabauer.banking.service.TransactionService;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route("account/:accountId(\\d+)")
public class AccountDetailView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AccountService accountService;
    private final TransactionService transactionService;

    private Long accountId;
    private Span balanceLabel;
    private Grid<Transaction> transactionGrid;

    @Inject
    public AccountDetailView(AccountService accountService,
                             TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String raw = event.getRouteParameters().get("accountId").orElse(null);
        if (raw == null) {
            event.forwardTo(AccountListView.class);
            return;
        }
        this.accountId = Long.parseLong(raw);
        Account account = accountService.findById(accountId).orElse(null);
        if (account == null) {
            Notification.show("Account not found").addThemeVariants(NotificationVariant.LUMO_ERROR);
            event.forwardTo(AccountListView.class);
            return;
        }
        buildUI(account);
        refresh();
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private void buildUI(Account account) {
        removeAll();
        setPadding(true);
        setSpacing(true);

        Button back = new Button("← All Accounts",
            e -> UI.getCurrent().navigate(AccountListView.class));
        add(back);

        add(new H2(account.getOwnerName() + "  —  " + account.getAccountNumber()));

        // Balance row
        balanceLabel = new Span();
        balanceLabel.getStyle()
            .set("font-size", "1.8em")
            .set("font-weight", "bold");
        HorizontalLayout balanceRow = new HorizontalLayout(new Span("Balance: "), balanceLabel);
        balanceRow.setAlignItems(Alignment.BASELINE);
        add(balanceRow);

        // Action buttons
        Button depositBtn  = new Button("Deposit",  e -> openDepositDialog());
        Button withdrawBtn = new Button("Withdraw", e -> openWithdrawDialog());
        Button transferBtn = new Button("Transfer", e -> openTransferDialog());
        depositBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        withdrawBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        transferBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(new HorizontalLayout(depositBtn, withdrawBtn, transferBtn));

        // Transaction history
        add(new H3("Transaction History"));
        transactionGrid = new Grid<>(Transaction.class, false);
        transactionGrid.addColumn(t -> t.getCreatedAt().format(DATE_FMT))
            .setHeader("Date").setSortable(true).setWidth("160px").setFlexGrow(0);
        transactionGrid.addColumn(t -> t.getType().name())
            .setHeader("Type").setWidth("110px").setFlexGrow(0);
        transactionGrid.addColumn(t -> {
            BigDecimal amt = t.getAmount().setScale(2, RoundingMode.HALF_UP);
            return (amt.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + amt.toPlainString() + " €";
        }).setHeader("Amount").setWidth("120px").setFlexGrow(0);
        transactionGrid.addColumn(t ->
            t.getTargetAccount() != null ? t.getTargetAccount().getAccountNumber() : "—"
        ).setHeader("Counter Account").setWidth("160px").setFlexGrow(0);
        transactionGrid.addColumn(Transaction::getDescription)
            .setHeader("Description");
        transactionGrid.setWidthFull();
        add(transactionGrid);
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private void openDepositDialog() {
        DepositWithdrawForm form = new DepositWithdrawForm(TransactionType.DEPOSIT);
        Dialog dialog = buildFormDialog("Deposit", form, () -> {
            BigDecimal amount = parseAmount(form.getAmountValue());
            if (amount == null) return;
            transactionService.deposit(accountId, amount, form.getDescriptionValue());
        });
        dialog.open();
    }

    private void openWithdrawDialog() {
        DepositWithdrawForm form = new DepositWithdrawForm(TransactionType.WITHDRAWAL);
        Dialog dialog = buildFormDialog("Withdraw", form, () -> {
            BigDecimal amount = parseAmount(form.getAmountValue());
            if (amount == null) return;
            try {
                transactionService.withdraw(accountId, amount, form.getDescriptionValue());
            } catch (InsufficientFundsException ex) {
                Notification.show("Insufficient funds: " + ex.getMessage())
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                throw ex; // re-throw to prevent dialog from closing in buildFormDialog
            }
        });
        dialog.open();
    }

    private void openTransferDialog() {
        List<Account> otherAccounts = accountService.findAll().stream()
            .filter(a -> !a.getId().equals(accountId))
            .toList();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Transfer");

        ComboBox<Account> targetCombo = new ComboBox<>("Target Account");
        targetCombo.setItems(otherAccounts);
        targetCombo.setItemLabelGenerator(Account::toString);
        targetCombo.setWidthFull();
        targetCombo.setRequiredIndicatorVisible(true);

        DepositWithdrawForm form = new DepositWithdrawForm(TransactionType.TRANSFER);

        Button cancel = new Button("Cancel", e -> dialog.close());
        Button confirm = new Button("Transfer", e -> {
            if (targetCombo.isEmpty()) {
                Notification.show("Please select a target account")
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            BigDecimal amount = parseAmount(form.getAmountValue());
            if (amount == null) return;
            try {
                transactionService.transfer(accountId, targetCombo.getValue().getId(),
                    amount, form.getDescriptionValue());
                dialog.close();
                refresh();
                Notification.show("Transfer successful").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (InsufficientFundsException ex) {
                Notification.show("Insufficient funds: " + ex.getMessage())
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(targetCombo, form);
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    /** Builds a simple dialog wrapping a DepositWithdrawForm with a confirm action. */
    private Dialog buildFormDialog(String title, DepositWithdrawForm form, Runnable action) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);

        Button cancel  = new Button("Cancel", e -> dialog.close());
        Button confirm = new Button("Confirm", e -> {
            try {
                action.run();
                dialog.close();
                refresh();
                Notification.show(title + " successful")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (RuntimeException ex) {
                // InsufficientFundsException is handled inside openWithdrawDialog;
                // other runtime exceptions bubble to the Vaadin error handler.
                if (!(ex instanceof InsufficientFundsException)) {
                    Notification.show("Error: " + ex.getMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(form);
        dialog.getFooter().add(cancel, confirm);
        return dialog;
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private void refresh() {
        BigDecimal balance = accountService.calculateCurrentBalance(accountId);
        balanceLabel.setText(balance.setScale(2, RoundingMode.HALF_UP).toPlainString() + " €");
        transactionGrid.setItems(transactionService.getTransactionHistory(accountId));
    }

    /** Parses the amount string and shows an error notification on failure. */
    private BigDecimal parseAmount(String raw) {
        try {
            BigDecimal val = new BigDecimal(raw.trim().replace(',', '.'));
            if (val.compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Amount must be greater than zero")
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return null;
            }
            return val;
        } catch (NumberFormatException ex) {
            Notification.show("Invalid amount: " + raw)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return null;
        }
    }
}
