package com.piccmaq.flutter_paypal_native.models;
import com.paypal.checkout.createorder.OrderIntent;
import java.util.HashMap;
import java.util.Map;

public class OrderIntentHelper {
    private final Map<String, OrderIntent> data = new HashMap<>();
    private final OrderIntent defaultIntent = OrderIntent.CAPTURE;
    private final String defaultIntentString = "CAPTURE";

    public OrderIntentHelper() {
        data.put("CAPTURE", OrderIntent.CAPTURE);
        data.put("AUTHORIZE", OrderIntent.AUTHORIZE);
    }

    public OrderIntent getEnumFromString(String which) {
        return data.containsKey(which.toUpperCase())
                ? data.get(which.toUpperCase())
                : defaultIntent;
    }

    public String getStringFromEnum(OrderIntent which) {
        for (Map.Entry<String, OrderIntent> entry : data.entrySet()) {
            if (entry.getValue() == which) {
                return entry.getKey();
            }
        }
        return defaultIntentString;
    }
}