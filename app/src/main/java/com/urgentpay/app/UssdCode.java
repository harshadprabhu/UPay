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
 * <h3>The option numbers are an NPCI standard, not a per-bank setting</h3>
 * {@code *99#} is a single NPCI platform (NUUP) that every member bank sits
 * behind — the bank name shown at the top of the menu is cosmetic. The public
 * NUUP specification documents the tree as
 * {@code 1 Send Money → 1.1 Mobile No. / 1.3 UPI ID / 1.4 Saved Beneficiary /
 * 1.5 IFSC+Account}, and the gap at 1.2 is a retired option ("Mobile Number &
 * MMID"), not a quirk of one bank.
 *
 * The digits are still exposed in Settings as an escape hatch, in case a
 * particular issuer or language pack ever reorders them, but the defaults are
 * expected to be correct everywhere.
 *
 * <h3>UPI IDs</h3>
 * The specification lists {@code *99*1*3*VPA*AMOUNT*REMARKS#} as a valid direct
 * string, so a UPI ID can be supplied inline. What is not guaranteed is that
 * the {@code @} survives the trip through Android's dialler, so the caller
 * tries the request API first and keeps the address on the clipboard as a
 * manual last resort.
 */
public class UssdCode {

    private static final String PREFS = "upay_bank_menu";

    private static final String KEY_SEND_MONEY = "opt_send_money";
    private static final String KEY_BY_MOBILE = "opt_by_mobile";
    private static final String KEY_BY_UPI_ID = "opt_by_upi_id";

    // Per the NUUP specification; the same on every member bank.
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
        /**
         * The string that is safe to hand to the dialler. Android strips
         * non-dialable characters from a {@code tel:} URI, so for a UPI ID this
         * is only the numeric prefix that reaches the "Enter UPI ID" prompt.
         */
        public final String code;
        /**
         * The complete string including a UPI ID, for the alphanumeric attempt
         * via {@code sendUssdRequest}. Null when {@link #code} is already complete.
         */
        public final String fullCode;
        /** Text to put on the clipboard for pasting, or null. */
        public final String clipboard;
        /** True when {@link #code} alone leaves nothing but the UPI PIN. */
        public final boolean fullyPrefilled;

        Dial(String code, String fullCode, String clipboard, boolean fullyPrefilled) {
            this.code = code;
            this.fullCode = fullCode;
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
            // Entirely numeric, so the dialler carries it verbatim.
            String code = "*99*" + sendMoney + "*" + byMobile + "*" + p;
            if (!TextUtils.isEmpty(amt)) {
                code += "*" + amt + "*" + SKIP_REMARK;
            }
            code += "#";
            return new Dial(code, code, null, !TextUtils.isEmpty(amt));
        }

        // The NUUP spec permits *99*1*3*VPA*AMOUNT*REMARKS#, but Android will not
        // carry it: the telephony layer strips every character it treats as
        // undialable before the request leaves the phone. Sending
        // "madhura.dudwadkar-4@oksbi" inline arrived at the bank as "4", which it
        // rejected with "4 is not a valid UPI ID."
        //
        // That failure was at least loud. The same stripping could equally
        // produce a *valid* address belonging to somebody else, and a UPI
        // transfer cannot be reversed. So a UPI ID is never placed in the dial
        // string: dial only as far as the "Enter UPI ID" prompt and hand the
        // address over via the clipboard, intact.
        String prefix = "*99*" + sendMoney + "*" + byUpiId + "#";
        return new Dial(prefix, null, p, false);
    }

    public static boolean isMobile(String s) {
        return s != null && s.trim().matches("^[6-9]\\d{9}$");
    }

}
