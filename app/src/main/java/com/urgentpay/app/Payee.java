package com.urgentpay.app;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A payee the user has saved at their bank via *99#, remembered here so UPay
 * can address them by the bank's own list number.
 *
 * That number is the whole point. Android strips letters and '@' out of
 * anything it dials, so a UPI ID can never travel inside a dial string — but a
 * beneficiary index is digits, and digits survive intact. A saved payee can
 * therefore be paid with one fully pre-filled string, leaving only the PIN.
 */
public class Payee {

    /** What the user calls them, e.g. "Madhura". */
    public final String nickname;
    /** Their UPI ID — shown for confirmation, never dialled. */
    public final String vpa;
    /** The number this payee appears as in the bank's saved-beneficiary list. */
    public final String index;

    public Payee(String nickname, String vpa, String index) {
        this.nickname = nickname == null ? "" : nickname.trim();
        this.vpa = vpa == null ? "" : vpa.trim();
        this.index = index == null ? "" : index.trim();
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("nickname", nickname);
        o.put("vpa", vpa);
        o.put("index", index);
        return o;
    }

    static Payee fromJson(JSONObject o) {
        return new Payee(
                o.optString("nickname"),
                o.optString("vpa"),
                o.optString("index"));
    }

    /** A payee is only one-shot payable if we know their bank list number. */
    public boolean isPayableInOneShot() {
        return index.matches("\\d{1,3}");
    }
}
