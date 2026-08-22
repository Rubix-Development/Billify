package nl.rubixstudios.billify.invoice.object;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InvoiceStatus {
    OPEN("open", "&6&lOpen"),
    PAID("paid", "&a&lPaid"),
    OVERDUE("overdue", "&4&lOverdue"),
    AUTOPAID("auto-paid", "&b&lAuto Paid"),
    CANCELLED("cancelled", "&4&lCancelled");

    private final String status;
    private final String prefix;
}
