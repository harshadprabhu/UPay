package com.urgentpay.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Launches the payment by dialling a *99# string that already carries the menu
 * choices, the payee and the amount — so the bank asks only for the UPI PIN.
 *
 * Nothing here touches another app's screen: UPay builds a dial string and
 * hands it to the dialler, exactly as if the user had typed it themselves. The
 * PIN is entered on the phone's own USSD screen and is never seen by UPay.
 */
public class UssdSessionActivity extends AppCompatActivity {

    public static final String EXTRA_VPA = "extra_vpa";
    public static final String EXTRA_AMOUNT = "extra_amount";

    private static final int REQ_CALL = 71;

    private String payee;
    private String amount;
    private UssdCode.Dial dial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ussd);

        payee = getIntent().getStringExtra(EXTRA_VPA);
        amount = getIntent().getStringExtra(EXTRA_AMOUNT);

        dial = new UssdCode(this).build(payee, amount);

        ((TextView) findViewById(R.id.tvSummaryPayee)).setText(payee);
        ((TextView) findViewById(R.id.tvSummaryAmount)).setText("₹" + amount);
        ((TextView) findViewById(R.id.tvDialCode)).setText(dial.code);

        TextView tvWhatsLeft = findViewById(R.id.tvWhatsLeft);
        View clipboardCard = findViewById(R.id.clipboardCard);

        if (dial.fullyPrefilled) {
            tvWhatsLeft.setText(R.string.only_pin_left);
            clipboardCard.setVisibility(View.GONE);
        } else if (dial.clipboard != null) {
            // Payee is a UPI ID — copy it so it's one long-press to paste.
            copyToClipboard(dial.clipboard);
            tvWhatsLeft.setText(R.string.paste_then_pin);
            clipboardCard.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.tvClipboardValue)).setText(dial.clipboard);
        } else {
            tvWhatsLeft.setText(R.string.answer_remaining);
            clipboardCard.setVisibility(View.GONE);
        }

        findViewById(R.id.btnDial).setOnClickListener(v -> placeCall());
        findViewById(R.id.btnCopyAgain).setOnClickListener(v -> {
            copyToClipboard(dial.clipboard);
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        });
    }

    private void copyToClipboard(String value) {
        if (value == null) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("UPI ID", value));
        }
    }

    /**
     * Dials the prepared string. ACTION_CALL runs it straight away; without the
     * permission we fall back to ACTION_DIAL, which pre-fills the dialler and
     * needs only a tap on the call button.
     */
    private void placeCall() {
        Uri uri = Uri.parse(UssdCode.toTelUri(dial.code));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
            return;
        }

        try {
            startActivity(new Intent(Intent.ACTION_CALL, uri));
        } catch (SecurityException | android.content.ActivityNotFoundException e) {
            openDialer(uri);
        }
    }

    private void openDialer(Uri uri) {
        Intent dialIntent = new Intent(Intent.ACTION_DIAL, uri);
        if (dialIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(dialIntent);
        } else {
            Toast.makeText(this, R.string.no_dialer, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALL) {
            Uri uri = Uri.parse(UssdCode.toTelUri(dial.code));
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                placeCall();
            } else {
                // Not a dead end — the dialler route still works.
                openDialer(uri);
            }
        }
    }
}
