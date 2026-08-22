package nl.rubixstudios.billify.invoice.object;

import lombok.Getter;
import lombok.Setter;
import nl.rubixstudios.billify.data.Language;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Getter
@Setter
public class Invoice {

    private final int invoiceId;
    private InvoiceStatus invoiceStatus;
    private final UUID invoiceAuthor;
    private double invoiceAmount;
    private final String invoiceReason;
    private final long invoiceDateTime;

    private int daysToPay;
    private long paymentDateTime;
    private UUID paidBy;

    private String cancelReason;
    private long canceledOnDateTime;
    private UUID canceledBy;

    private long lastReminderTime;
    private boolean collectionFeeApplied;

    public Invoice(int invoiceId, InvoiceStatus invoiceStatus, UUID invoiceAuthor, double invoiceAmount, String invoiceReason, Date invoiceDate) {
        this.invoiceId = invoiceId;
        this.invoiceStatus = invoiceStatus;
        this.invoiceAuthor = invoiceAuthor;
        this.invoiceAmount = invoiceAmount;
        this.invoiceReason = invoiceReason;
        this.invoiceDateTime = invoiceDate.getTime();
    }

    public Date getDateToPay() {
        return new Date(this.invoiceDateTime + (this.daysToPay * 86400000L));
    }

    public int getDaysLeft() {
        return (int) ((this.getDateToPay().getTime() - new Date().getTime()) / 86400000L);
    }

    public int getDaysOverdue() {
        return Math.max(0, -this.getDaysLeft());
    }

    private static SimpleDateFormat formatter() {
        String format = Language.getMessage("INVOICE.DATE_FORMAT");
        if (format == null || format.isEmpty()) format = "dd-MM-yyyy hh:mm a";
        return new SimpleDateFormat(format);
    }

    public String getDateToPayInString() {
        String suffix = Language.getMessage("INVOICE.DAYS_LEFT_SUFFIX");
        if (suffix == null) suffix = " (%days% days left)";
        return formatter().format(this.getDateToPay()) + suffix.replace("%days%", String.valueOf(this.getDaysLeft()));
    }

    public String getDateInvoicePaidToString() {
        return formatter().format(new Date(this.paymentDateTime));
    }

    public String getDateInvoiceCanceledToString() {
        return formatter().format(new Date(this.canceledOnDateTime));
    }
}
