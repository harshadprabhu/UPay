package com.urgentpay.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Saved payees, kept on the device only. Nothing here is uploaded and the app
 * holds no INTERNET permission, so this list cannot leave the phone.
 */
public class PayeeStore {

    private static final String PREFS = "upay_payees";
    private static final String KEY = "payees";

    private final SharedPreferences prefs;

    public PayeeStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Payee> all() {
        List<Payee> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(Payee.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Corrupt store: better an empty list than a crash on the home screen.
        }
        return out;
    }

    /** Adds a payee, replacing any existing entry with the same UPI ID. */
    public void save(Payee payee) {
        List<Payee> list = all();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).vpa.equalsIgnoreCase(payee.vpa)) {
                list.remove(i);
                break;
            }
        }
        list.add(payee);
        persist(list);
    }

    public void remove(String vpa) {
        List<Payee> list = all();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).vpa.equalsIgnoreCase(vpa)) {
                list.remove(i);
                break;
            }
        }
        persist(list);
    }

    public Payee findByVpa(String vpa) {
        if (vpa == null) return null;
        for (Payee p : all()) {
            if (p.vpa.equalsIgnoreCase(vpa.trim())) return p;
        }
        return null;
    }

    private void persist(List<Payee> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Payee p : list) arr.put(p.toJson());
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (JSONException e) {
            // Nothing sensible to do; the previous list stays in place.
        }
    }
}
