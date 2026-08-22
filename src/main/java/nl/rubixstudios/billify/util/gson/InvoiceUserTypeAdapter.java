package nl.rubixstudios.billify.util.gson;

import com.google.gson.*;
import nl.rubixstudios.billify.invoice.InvoiceUser;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InvoiceUserTypeAdapter implements JsonSerializer<Map<UUID, InvoiceUser>>, JsonDeserializer<Map<UUID, InvoiceUser>> {

    @Override
    public JsonElement serialize(Map<UUID, InvoiceUser> map, Type type, JsonSerializationContext context) {
        final JsonArray array = new JsonArray();

        map.values().forEach(invoiceUser -> array.add(context.serialize(invoiceUser, invoiceUser.getClass())));

        return array;
    }

    @Override
    public Map<UUID, InvoiceUser> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        final JsonArray array = json.getAsJsonArray();
        Map<UUID, InvoiceUser> invoiceUserMap = new HashMap<>();

        InvoiceUser invoiceUser;

        for (JsonElement element : array) {
            invoiceUser = context.deserialize(element.getAsJsonObject(), InvoiceUser.class);
            invoiceUserMap.put(invoiceUser.getPlayerId(), invoiceUser);
        }

        return invoiceUserMap;
    }
}
