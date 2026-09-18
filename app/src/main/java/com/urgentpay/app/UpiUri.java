package com.urgentpay.app;

import android.net.Uri;
import android.text.TextUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a payee out of whatever a UPI QR code actually contains.
 *
 * Real-world QRs are not all the tidy {@code upi://pay?pa=…} deep link. The
 * four shapes that turn up in practice, all handled here and all parsed purely
 * on-device:
 *
 * <ol>
 *   <li><b>UPI deep link</b> — {@code upi://pay?pa=x@bank&pn=Name&am=100}, and the
 *       app-specific variants ({@code tez://}, {@code phonepe://}, {@code paytmmp://}).</li>
 *   <li><b>EMVCo / BharatQR</b> — the TLV format most merchant QRs (including
 *       Google Pay and PhonePe merchant codes) are printed in. Looks like
 *       {@code 00020101021226…} and contains no "upi:" anywhere.</li>
 *   <li><b>An https link</b> that carries the UPI parameters in its query.</li>
 *   <li><b>Bare text</b> — a UPI ID or a 10-digit mobile number.</li>
 * </ol>
 *
 * Anything unrecognised falls through to a last-resort scan for a UPI-ID-shaped
 * substring, because a QR that contains a payable address in <em>some</em> form
 * is far more common than one that contains none.
 */
public class UpiUri {

    public final String vpa;      // payee address (UPI ID) or mobile number
    public final String name;     // payee name, may be empty
    public final String amount;   // amount, may be empty
    public final String note;     // transaction note, may be empty

    private static final Pattern MOBILE = Pattern.compile("^[6-9]\\d{9}$");

    /** A UPI handle: local part, '@', then the bank handle. */
    private static final Pattern VPA_PATTERN =
            Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._\\-]{1,255}@[a-zA-Z][a-zA-Z0-9.\\-]{1,63}$");

    /** The same shape, used to hunt for a VPA inside a larger blob of text. */
    private static final Pattern VPA_IN_TEXT =
            Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._\\-]{1,255}@[a-zA-Z][a-zA-Z0-9.\\-]{1,63}");

    private UpiUri(String vpa, String name, String amount, String note) {
        this.vpa = vpa;
        this.name = name == null ? "" : name.trim();
        this.amount = normaliseAmount(amount);
        this.note = note == null ? "" : note.trim();
    }

    /** Strips a trailing ".00" and anything non-numeric the QR may carry. */
    private static String normaliseAmount(String a) {
        if (TextUtils.isEmpty(a)) return "";
        String s = a.trim().replaceAll("[^0-9.]", "");
        if (s.isEmpty()) return "";
        if (s.endsWith(".00")) s = s.substring(0, s.length() - 3);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return "0".equals(s) ? "" : s;
    }

    /** Returns a parsed payee, or null if nothing payable could be found. */
    public static UpiUri parse(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        raw = raw.trim();

        UpiUri fromLink = fromDeepLink(raw);
        if (fromLink != null) return fromLink;

        UpiUri fromEmv = fromEmv(raw);
        if (fromEmv != null) return fromEmv;

        // Bare UPI ID or mobile number typed or encoded directly.
        if (isValidVpa(raw)) return new UpiUri(raw, "", "", "");
        if (MOBILE.matcher(raw).matches()) return new UpiUri(raw, "", "", "");

        // Last resort: find a UPI-ID-shaped token anywhere in the payload.
        Matcher m = VPA_IN_TEXT.matcher(raw);
        while (m.find()) {
            String candidate = m.group();
            if (isValidVpa(candidate)) {
                return new UpiUri(candidate, "", "", "");
            }
        }
        return null;
    }

    /** Handles upi://, tez://, phonepe://, paytmmp:// and https:// links. */
    private static UpiUri fromDeepLink(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        boolean looksLikeLink = lower.startsWith("upi:") || lower.startsWith("tez:")
                || lower.startsWith("phonepe:") || lower.startsWith("paytmmp:")
                || lower.startsWith("bhim:") || lower.startsWith("gpay:")
                || lower.startsWith("http:") || lower.startsWith("https:");
        if (!looksLikeLink) return null;

        try {
            Uri uri = Uri.parse(raw);
            String pa = firstParam(uri, "pa", "payeeAddress", "vpa");
            if (isValidVpa(pa)) {
                return new UpiUri(pa.trim(),
                        firstParam(uri, "pn", "payeeName"),
                        firstParam(uri, "am", "amount"),
                        firstParam(uri, "tn", "note"));
            }
        } catch (Exception ignored) {
            // Malformed URI — the caller's fallbacks still apply.
        }
        return null;
    }

    private static String firstParam(Uri uri, String... keys) {
        for (String k : keys) {
            try {
                String v = uri.getQueryParameter(k);
                if (!TextUtils.isEmpty(v)) return v;
            } catch (Exception ignored) {
                // Not a hierarchical URI; nothing to read.
            }
        }
        return "";
    }

    // ---- EMVCo / BharatQR ----------------------------------------------

    /**
     * Parses the EMVCo TLV format used by most printed merchant QRs.
     *
     * The payload is a flat run of {@code TTLLvalue} entries. The payee lives
     * inside one of the "merchant account information" templates (tags 26–51),
     * which are themselves TLV. Rather than assume a fixed sub-tag number —
     * issuers differ — every value in those templates is tested against the UPI
     * ID shape and the first match wins.
     */
    private static UpiUri fromEmv(String raw) {
        // Every EMVCo payload starts with the payload format indicator "000201".
        if (!raw.startsWith("0002")) return null;

        Map<String, String> top = tlv(raw);
        if (top.isEmpty()) return null;

        String vpa = null;
        for (int tag = 26; tag <= 51 && vpa == null; tag++) {
            String template = top.get(String.format(Locale.ROOT, "%02d", tag));
            if (template == null) continue;
            for (String value : tlv(template).values()) {
                if (isValidVpa(value)) {
                    vpa = value;
                    break;
                }
            }
        }
        if (vpa == null) return null;

        String amount = top.get("54");   // transaction amount
        String name = top.get("59");     // merchant name
        return new UpiUri(vpa, name, amount, "");
    }

    /** Splits an EMVCo payload into its tag → value entries. */
    private static Map<String, String> tlv(String s) {
        Map<String, String> out = new LinkedHashMap<>();
        int i = 0;
        while (i + 4 <= s.length()) {
            String tag = s.substring(i, i + 2);
            int len;
            try {
                len = Integer.parseInt(s.substring(i + 2, i + 4));
            } catch (NumberFormatException e) {
                break; // Not TLV after all.
            }
            int start = i + 4;
            int end = start + len;
            if (len < 0 || end > s.length()) break;
            out.put(tag, s.substring(start, end));
            i = end;
        }
        return out;
    }

    // ---- helpers --------------------------------------------------------

    public static boolean isValidVpa(String s) {
        if (TextUtils.isEmpty(s)) return false;
        String t = s.trim();
        // An amount or a numeric blob can contain '@' in odd QRs; require a letter.
        return VPA_PATTERN.matcher(t).matches() && t.matches(".*[a-zA-Z].*");
    }

    public boolean isMobile() {
        return MOBILE.matcher(vpa).matches();
    }

    public boolean hasName() {
        return !TextUtils.isEmpty(name);
    }
}
