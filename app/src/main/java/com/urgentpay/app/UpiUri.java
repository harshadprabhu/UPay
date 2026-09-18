package com.urgentpay.app;

import android.net.Uri;
import android.text.TextUtils;

import java.util.regex.Pattern;

/**
 * Parses a UPI deep link (the payload inside a standard UPI QR code), e.g.
 * {@code upi://pay?pa=merchant@bank&pn=Merchant%20Name&am=100.00&cu=INR}.
 *
 * Parsing is done entirely on-device with no network access.
 */
public class UpiUri {

    public final String vpa;      // pa= payee address (UPI ID)
    public final String name;     // pn= payee name
    public final String amount;   // am= amount (may be empty)
    public final String note;     // tn= transaction note

    private static final Pattern MOBILE = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern VPA_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$");

    private UpiUri(String vpa, String name, String amount, String note) {
        this.vpa = vpa;
        this.name = name;
        this.amount = amount;
        this.note = note;
    }

    /** Returns a parsed UpiUri, or null if the text is not a usable UPI target. */
    public static UpiUri parse(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        raw = raw.trim();

        // Case 1: a full upi:// deep link (from a scanned QR).
        if (raw.toLowerCase().startsWith("upi:")) {
            try {
                Uri uri = Uri.parse(raw);
                String pa = uri.getQueryParameter("pa");
                if (isValidVpa(pa)) {
                    String pn = safe(uri.getQueryParameter("pn"));
                    String am = safe(uri.getQueryParameter("am"));
                    String tn = safe(uri.getQueryParameter("tn"));
                    return new UpiUri(pa.trim(), pn, am, tn);
                }
            } catch (Exception ignored) {
                return null;
            }
            return null;
        }

        // Case 2: a bare UPI ID typed by the user.
        if (isValidVpa(raw)) {
            return new UpiUri(raw, "", "", "");
        }

        // Case 3: a bare 10-digit mobile number typed by the user.
        if (MOBILE.matcher(raw).matches()) {
            return new UpiUri(raw, "", "", "");
        }

        return null;
    }

    public static boolean isValidVpa(String s) {
        return !TextUtils.isEmpty(s) && VPA_PATTERN.matcher(s.trim()).matches();
    }

    public boolean hasName() {
        return !TextUtils.isEmpty(name);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
