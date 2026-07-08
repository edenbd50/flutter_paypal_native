package com.piccmaq.flutter_paypal_native;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.paypal.checkout.PayPalCheckout;
import com.paypal.checkout.config.CheckoutConfig;
import com.paypal.checkout.config.Environment;
import com.paypal.checkout.createorder.CurrencyCode;
import com.paypal.checkout.createorder.OrderIntent;
import com.paypal.checkout.createorder.UserAction;
import com.paypal.checkout.order.Amount;
import com.paypal.checkout.order.AppContext;
import com.paypal.checkout.order.OrderRequest;
import com.paypal.checkout.order.PurchaseUnit;
import com.piccmaq.flutter_paypal_native.models.CheckoutConfigStore;
import com.piccmaq.flutter_paypal_native.models.CurrencyCodeHelper;
import com.piccmaq.flutter_paypal_native.models.EnvironmentHelper;
import com.piccmaq.flutter_paypal_native.models.OrderIntentHelper;
import com.piccmaq.flutter_paypal_native.models.PayPalCallBackHelper;
import com.piccmaq.flutter_paypal_native.models.PurchaseUnitC;
import com.piccmaq.flutter_paypal_native.models.PurchaseUnitHelper;
import com.piccmaq.flutter_paypal_native.models.UserActionHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// Magnes device-risk SDK — already bundled inside com.paypal.checkout:android-sdk (libs/android-magnessdk-*.jar),
// so we call it directly (no extra dependency, no duplicate-class conflict). NOTE: `Environment` here would
// clash with com.paypal.checkout.config.Environment, so the Magnes Environment is used fully-qualified below.
import lib.android.com.paypal.magnessdk.MagnesResult;
import lib.android.com.paypal.magnessdk.MagnesSDK;
import lib.android.com.paypal.magnessdk.MagnesSettings;
import lib.android.com.paypal.magnessdk.MagnesSource;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/** FlutterPaypalNativePlugin */
public class FlutterPaypalNativePlugin extends FlutterRegistrarResponder
        implements FlutterPlugin, MethodCallHandler, ActivityAware {
    boolean initialisedPaypalConfig = false;

    private Application application;
    private CheckoutConfigStore checkoutConfigStore;
    private PayPalCallBackHelper payPalCallBackHelper;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_paypal_native");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
            return;
        } else if (call.method.equals("FlutterPaypal#initiate")) {
            initiatePackage(call, result);
            return;
        } else if (call.method.equals("FlutterPaypal#makeOrder")) {
            makeOrder(call, result);
            return;
        } else if (call.method.equals("FlutterPaypal#collectClientMetadataId")) {
            collectClientMetadataId(call, result);
            return;
        }
        result.notImplemented();
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        application = binding.getActivity().getApplication();
        initialisePaypalConfig();
    }

    @Override
    public void onDetachedFromActivity() {
        application = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
    }

    private void initiatePackage(@NonNull MethodCall call, @NonNull Result result) {
        String returnUrl = call.argument("returnUrl");
        String clientId = call.argument("clientId");
        String payPalEnvironmentStr = call.argument("payPalEnvironment");
        String currencyStr = call.argument("currency");
        String userActionStr = call.argument("userAction");
        String intentStr = call.argument("intent");

        CurrencyCode currency = (new CurrencyCodeHelper()).getEnumFromString(currencyStr);
        UserAction userAction = (new UserActionHelper()).getEnumFromString(userActionStr);
        Environment payPalEnvironment = (new EnvironmentHelper()).getEnumFromString(payPalEnvironmentStr);
        OrderIntent intent = (new OrderIntentHelper()).getEnumFromString(intentStr);

        // store in checkoutconfigstore because application is sometimes null
        checkoutConfigStore = new CheckoutConfigStore(
                clientId,
                payPalEnvironment,
                intent,
                returnUrl,
                currency,
                userAction);
        result.success("completed");
    }

    void initialisePaypalConfig() {
        if (application == null)
            return;
        if (checkoutConfigStore == null)
            return;

        PayPalCheckout.setConfig(new CheckoutConfig(
                application,
                checkoutConfigStore.clientId,
                checkoutConfigStore.payPalEnvironment,
                checkoutConfigStore.currency,
                checkoutConfigStore.userAction,
                checkoutConfigStore.returnUrl));
        payPalCallBackHelper = new PayPalCallBackHelper(this);
        PayPalCheckout.registerCallbacks(
                approval -> {
                    // Order successfully captured or autorized
                    payPalCallBackHelper.onPayPalApprove(approval, checkoutConfigStore.intent);
                },
                (shippingData, shippingAction) -> {
                    // called when shippinginfo changes
                    payPalCallBackHelper.onPayPalShippingChange(shippingData, shippingAction);
                },
                () -> {
                    // Optional callback for when a buyer cancels the paysheet
                    payPalCallBackHelper.onPayPalCancel();
                },
                errorInfo -> {
                    // Optional error callback
                    payPalCallBackHelper.onPayPalError(errorInfo);
                });
        initialisedPaypalConfig = true;
    }

    private void makeOrder(@NonNull MethodCall call, @NonNull Result result) {
        if (!initialisedPaypalConfig) {
            initialisePaypalConfig();
        }

        String purchaseUnitsStr = call.argument("purchaseUnits");
        String userActionStr = call.argument("userAction");
        String intentStr = call.argument("intent");
        // v6 server-authoritative: when present, approve THIS server-created order id instead of
        // building purchase units client-side (the server set the intent + PayPal-Client-Metadata-Id).
        String serverOrderId = call.argument("orderId");
        UserAction userAction = (new UserActionHelper()).getEnumFromString(userActionStr);
        OrderIntent orderIntent = (new OrderIntentHelper()).getEnumFromString(intentStr);

        List<PurchaseUnitC> purchaseUnitsC = (new PurchaseUnitHelper())
                .convertJsonToArrayList(purchaseUnitsStr);
        CurrencyCodeHelper helper = (new CurrencyCodeHelper());

        try {
            PayPalCheckout.startCheckout(
                    createOrderActions -> {
                        if (serverOrderId != null && !serverOrderId.isEmpty()) {
                            createOrderActions.set(serverOrderId);
                            return;
                        }
                        ArrayList<PurchaseUnit> purchaseUnits = new ArrayList<>();
                        for (PurchaseUnitC purchaseUnit : purchaseUnitsC) {
                            CurrencyCode currency = helper.getEnumFromString(
                                    purchaseUnit.getCurrency());
                            purchaseUnits.add(
                                    new PurchaseUnit.Builder()
                                            .amount(
                                                    new Amount.Builder()
                                                            .currencyCode(currency)
                                                            .value(purchaseUnit.getPrice())
                                                            .build())
                                            .referenceId(purchaseUnit.getReferenceID())
                                            .build());
                        }
                        OrderRequest order = new OrderRequest(
                                orderIntent,
                                new AppContext.Builder()
                                        .userAction(userAction)
                                        .build(),
                                purchaseUnits);
                        createOrderActions.create(order, orderId -> {
                        });
                    });
            result.success("completed");
        } catch (Exception e) {
            Toast.makeText(application, "error occured while getting order", Toast.LENGTH_SHORT).show();

            result.error("completed", e.getMessage(), e.getMessage());
        }
    }

    // Collects + submits device-risk data via Magnes (bundled in the checkout SDK) under the supplied
    // client-metadata-id, so the server-forwarded `PayPal-Client-Metadata-Id` correlates with a real
    // device session (the v6 "supply your own id" model, mirroring web's createInstance). Returns the id
    // actually used — sanitized to Magnes' <=32-char alphanumeric limit — which the caller MUST send to
    // create-order. Never blocks checkout: any failure falls back to the suggested id.
    private void collectClientMetadataId(@NonNull MethodCall call, @NonNull Result result) {
        String suggested = call.argument("clientMetadataId");
        try {
            if (application == null) {
                result.success(suggested);
                return;
            }
            // Magnes requires an alphanumeric id <= 32 chars; a UUID becomes 32 hex chars once hyphens drop.
            String cmid = suggested == null ? null : suggested.replace("-", "");
            if (cmid != null && cmid.length() > 32) {
                cmid = cmid.substring(0, 32);
            }

            lib.android.com.paypal.magnessdk.Environment magnesEnv =
                    (checkoutConfigStore != null
                            && checkoutConfigStore.payPalEnvironment == Environment.SANDBOX)
                            ? lib.android.com.paypal.magnessdk.Environment.SANDBOX
                            : lib.android.com.paypal.magnessdk.Environment.LIVE;

            MagnesSettings settings = new MagnesSettings.Builder(application)
                    .setMagnesEnvironment(magnesEnv)
                    .setMagnesSource(MagnesSource.PAYPAL)
                    .disableBeacon(false)
                    .build();
            MagnesSDK.getInstance().setUp(settings);

            MagnesResult magnesResult = MagnesSDK.getInstance()
                    .collectAndSubmit(application, cmid, new HashMap<String, String>());
            String used = magnesResult != null ? magnesResult.getPaypalClientMetaDataId() : null;
            result.success((used != null && !used.isEmpty()) ? used : cmid);
        } catch (Exception e) {
            // Fraud-signal collection must never break the payment; fall back to the suggested id.
            result.success(suggested);
        }
    }

}
