package dev.rabauer.banking.view;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextField;
import dev.rabauer.banking.entity.TransactionType;

/**
 * Reusable form component for Deposit, Withdrawal, and Transfer dialogs.
 * Not a CDI bean — instantiated directly by the views that use it.
 */
public class DepositWithdrawForm extends FormLayout {

    private final TextField amountField;
    private final TextField descriptionField;

    public DepositWithdrawForm(TransactionType type) {
        String amountLabel = switch (type) {
            case DEPOSIT    -> "Deposit Amount (€)";
            case WITHDRAWAL -> "Withdrawal Amount (€)";
            case TRANSFER   -> "Transfer Amount (€)";
        };

        amountField = new TextField(amountLabel);
        amountField.setPlaceholder("0.00");
        amountField.setWidthFull();
        amountField.setRequiredIndicatorVisible(true);

        descriptionField = new TextField("Description (optional)");
        descriptionField.setWidthFull();
        descriptionField.setPlaceholder("e.g. Salary, Rent, ...");

        setResponsiveSteps(new ResponsiveStep("0", 1));
        add(amountField, descriptionField);
    }

    public String getAmountValue() {
        return amountField.getValue();
    }

    public String getDescriptionValue() {
        String val = descriptionField.getValue().trim();
        return val.isBlank() ? null : val;
    }

    public void focusAmount() {
        amountField.focus();
    }
}
