package com.urgentpay.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Runs the *99# rail and renders the bank's replies inside the app instead of
 * the raw system overlay, using {@link TelephonyManager#sendUssdRequest}
 * (Android 8 / API 26+).
 *
 * Honest limitations, surfaced to the user rather than hidden:
 *  - sendUssdRequest is a single request/response call. Deep interactive menu
 *    navigation and, critically, UPI PIN entry are NOT done here — for those we
 *    hand off to the phone's secure dialer (the only compliant place for a PIN).
 *  - Some devices/carriers don't support programmatic USSD. We detect that and
 *    fall back to the secure dialer automatically.
 */
public class UssdSessionActivity extends AppCompatActivity {

    public static final String EXTRA_VPA = "extra_vpa";
    public static final String EXTRA_AMOUNT = "extra_amount";

    private static final int REQ_CALL = 71;

    private TextView tvStatus;
    private TextView tvResponse;
    private EditText etReply;

    private String vpa;
    private String amount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ussd);

        vpa = getIntent().getStringExtra(EXTRA_VPA);
        amount = getIntent().getStringExtra(EXTRA_AMOUNT);

        tvStatus = findViewById(R.id.tvUssdStatus);
        tvResponse = findViewById(R.id.tvUssdResponse);
        etReply = findViewById(R.id.etReply);

        findViewById(R.id.btnSendReply).setOnClickListener(v -> sendReply());
        findViewById(R.id.btnSecureDialer).setOnClickListener(v -> openSecureDialer());

        append("Recipient: " + vpa + "\nAmount: ₹" + amount + "\n");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Programmatic USSD needs API 26. Older devices go straight to dialer.
            tvStatus.setText(R.string.ussd_unsupported);
            openSecureDialer();
            return;
        }
        ensurePermissionThenStart();
    }

    private void ensurePermissionThenStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
            runUssd(getString(R.string.ussd_code));
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void runUssd(String code) {
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (tm == null) {
            fallback();
            return;
        }
        tvStatus.setText(R.string.ussd_dialing);
        try {
            tm.sendUssdRequest(code, new TelephonyManager.UssdResponseCallback() {
                @Override
                public void onReceiveUssdResponse(TelephonyManager t, String request, CharSequence response) {
                    tvStatus.setText(R.string.offline_session);
                    append("\n" + request + " →\n" + response + "\n");
                }

                @Override
                public void onReceiveUssdResponseFailed(TelephonyManager t, String request, int failureCode) {
                    tvStatus.setText(R.string.ussd_failed);
                    append("\n[Session ended by network]\n");
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (SecurityException e) {
            fallback();
        }
    }

    private void sendReply() {
        String reply = etReply.getText().toString().trim();
        if (TextUtils.isEmpty(reply)) return;
        etReply.setText("");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // A menu selection is itself a USSD string on the *99# tree.
            runUssd(reply);
        } else {
            openSecureDialer();
        }
    }

    private void fallback() {
        Toast.makeText(this, R.string.ussd_unsupported, Toast.LENGTH_LONG).show();
        openSecureDialer();
    }

    /**
     * Hand off to the phone's dialer with *99# prefilled. This is where the
     * UPI PIN is entered — on the secure, bank-owned screen, never in our UI.
     */
    private void openSecureDialer() {
        String code = getString(R.string.ussd_code).replace("#", "%23");
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + code));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_dialer, Toast.LENGTH_LONG).show();
        }
    }

    private void append(String s) {
        tvResponse.append(s);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALL) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runUssd(getString(R.string.ussd_code));
            } else {
                Toast.makeText(this, R.string.perm_needed, Toast.LENGTH_LONG).show();
                openSecureDialer();
            }
        }
    }
}
