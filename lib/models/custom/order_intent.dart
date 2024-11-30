enum FPayPalOrderIntent {

  ///  Default. The merchant intends to capture payment immediately after the customer makes a payment.
  capture,

  /// The merchant intends to authorize a payment and place funds on hold after the customer makes a payment.
  /// Authorized payments are guaranteed for up to 3 days but are available to capture for up to 29 days.
  /// After the 3-day honor period, the original authorized payment expires and you need to re-authorize the payment.
  /// You need to make a separate request to capture payments on demand.
  /// This intent isn't supported when you have more than 1 purchase_unit within your order.
  authorize

}

class FPayPalOrderIntentHelper {
  static const Map<FPayPalOrderIntent, String> codes = {
    FPayPalOrderIntent.authorize: "AUTHORIZE",
    FPayPalOrderIntent.capture: "CAPTURE"
  };

  //convert enum to string
  static String convertFromEnumToString(FPayPalOrderIntent envv) {
    if (codes[envv] != null) {
      return codes[envv]!;
    }
    return codes[FPayPalOrderIntent.capture]!;
  }
}
