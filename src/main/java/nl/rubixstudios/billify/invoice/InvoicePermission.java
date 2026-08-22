package nl.rubixstudios.billify.invoice;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InvoicePermission {

    private final String rank;
    private final String permission;
    private final double limit;

    private final boolean canCreateInvoice;
    private final boolean canCancelInvoice;
    private final boolean canCheckInvoices;
}
