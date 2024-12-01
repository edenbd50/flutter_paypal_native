import PayPalCheckout

extension PayPalCheckout.OrderIntent {
    init(rawValueString: String) {
        switch rawValueString {
        case "authorize":
            self = .authorize
        case "capture":
            self = .capture
        default:
            self = .capture
        }
    }
}