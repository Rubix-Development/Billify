package nl.rubixstudios.billify.invoice;

import lombok.Getter;
import lombok.Setter;
import nl.rubixstudios.billify.invoice.object.Invoice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class InvoiceUser {

    private final UUID playerId;

    private final List<Invoice> invoices;

    public InvoiceUser(UUID playerId) {
        this.playerId = playerId;

        this.invoices = new ArrayList<>();
    }
}
