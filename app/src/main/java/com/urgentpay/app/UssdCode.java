package com.urgentpay.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * Builds a "deep" *99# dial string so the bank's menus are answered up front
 * instead of one slow round trip at a time.
 *
 * The *99# service accepts menu choices as extra {@code *}-separated fields in
 * the dialled string, so
 *
 * <pre>*99*1*1*9876543210*500*1#</pre>
 *
 * means: Send Money (1) → by Mobile No. (1) → that number → that amount →
 * skip the remark (1). The only thing left on screen is the UPI PIN box.
 *
 * <h3>Why the option numbers are configurable</h3>
 * They are not the same at every bank. One real HDFC menu reads
 * {@code 1. Mobile No. / 3. UPI ID / 4. Saved Beneficiary / 5. IFSC, A/C No.} —
 * note that it skips 2 entirely. Hard-coding those digits would silently send
 * money down the wrong menu branch at another bank, so they are stored as
 * preferences the user can correct from Settings.
 *
 * <h3>UPI IDs</h3>
 * A UPI ID contains {@code @}, {@code .} and {@code -}, which are not reliably
 * carried by a USSD dial string. Rather than risk a malformed request, a
 * payment to a UPI ID is dialled only as deep as the "Enter UPI ID" prompt and
 * the address is placed on the clipboard for a one-tap paste.
 */
public class UssdCode {

    private static final String PREFS = "upay_bank_menu";

    private static final String KEY_SEND_MONEY = "opt_send_money";
    private static final String KEY_BY_MOBILE = "opt_by_mobile";
    private static final String KEY_BY_UPI_ID = "opt_by_upi_id";

    // Defaults observed on a live HDFC *99# menu.
    private static final String DEF_SEND_MONEY = "1";
    private static final String DEF_BY_MOBILE = "1";
    private static final String DEF_BY_UPI_ID = "3";

    /** Answer given to "Enter a remark (Enter 1 to skip)". */
    private static final String SKIP_REMARK = "1";

    private final String sendMoney;
    private final String byMobile;
    private final String byUpiId;

    public UssdCode(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sendMoney = p.getString(KEY_SEND_MONEY, DEF_SEND_MONEY);
        byMobile = p.getString(KEY_BY_MOBILE, DEF_BY_MOBILE);
        byUpiId = p.getString(KEY_BY_UPI_ID, DEF_BY_UPI_ID);
    }

    public static void save(Context context, String sendMoney, String byMobile, String byUpiId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_SEND_MONEY, sendMoney)
                .putString(KEY_BY_MOBILE, byMobile)
                .putString(KEY_BY_UPI_ID, byUpiId)
                .apply();
    }

    public String getSendMoney() { return sendMoney; }
    public String getByMobile() { return byMobile; }
    public String getByUpiId() { return byUpiId; }

    /** What the app managed to pre-answer, so the UI can say so honestly. */
    public static class Dial {
        /** The string to dial, e.g. {@code *99*1*1*9876543210*500*1#}. */
        public final String code;
        /** Text to put on the clipboard for pasting, or null. */
        public final String clipboard;
        /** True when only the UPI PIN should remain. */
        public final boolean fullyPrefilled;

        Dial(String code, String clipboard, boolean fullyPrefilled) {
            this.code = code;
            this.clipboard = clipboard;
            this.fullyPrefilled = fullyPrefilled;
        }
    }

    /**
     * @param payee  a 10-digit mobile number or a UPI ID
     * @param amount rupee amount, digits only
     */
    public Dial build(String payee, String amount) {
        String p = payee == null ? "" : payee.trim();
        String amt = amount == null ? "" : amount.trim();

        if (isMobile(p)) {
            // Everything is numeric, so the whole branch can be pre-answered.
            String code = "*99*" + sendMoney + "*" + byMobile + "*" + p;
            if (!TextUtils.isEmpty(amt)) {
                code += "*" + amt + "*" + SKIP_REMARK;
            }
            return new Dial(code + "#", null, !TextUtils.isEmpty(amt));
        }

        // UPI ID: go straight to the "Enter UPI ID" prompt and hand the address
        // over via the clipboard instead of risking it inside the dial string.
        String code = "*99*" + sendMoney + "*" + byUpiId + "#";
        return new Dial(code, p, false);
    }

    public static boolean isMobile(String s) {
        return s != null && s.trim().matches("^[6-9]\\d{9}$");
    }

    /** Percent-encodes '#' so the dialler keeps it instead of treating it as a fragment. */
    public static String toTelUri(String code) {
        return "tel:" + code.replace("#", "%23");
    }
}
