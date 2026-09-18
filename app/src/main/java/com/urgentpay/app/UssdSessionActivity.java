package com.urgentpay.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
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
    public static final String EXTRA_BENEFICIARY_INDEX = "extra_beneficiary_index";

    private static final int REQ_CALL = 71;

    private String payee;
    private String amount;
    private UssdCode codes;
    private UssdCode.Dial dial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ussd);

        payee = getIntent().getStringExtra(EXTRA_VPA);
        amount = getIntent().getStringExtra(EXTRA_AMOUNT);
        String beneficiaryIndex = getIntent().getStringExtra(EXTRA_BENEFICIARY_INDEX);

        codes = new UssdCode(this);
        // A saved payee has a numeric list number at the bank, which survives the
        // dial-string stripping that a UPI ID does not — so this route alone can
        // pre-fill a UPI-ID payment completely.
        dial = (beneficiaryIndex != null && beneficiaryIndex.matches("\\d{1,3}"))
                ? codes.buildForBeneficiary(beneficiaryIndex, amount)
                : codes.build(payee, amount);

        ((TextView) findViewById(R.id.tvSummaryPayee)).setText(payee);
        ((TextView) findViewById(R.id.tvSummaryAmount)).setText("₹" + amount);

        findViewById(R.id.btnDial).setOnClickListener(v -> placeCall());
        findViewById(R.id.btnCopyAgain).setOnClickListener(v -> {
            copyToClipboard(dial.clipboard);
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        });
        // Paying the mobile number inside a UPI ID is a different instruction to
        // the bank, so the user keeps a way back to the exact address.
        findViewById(R.id.btnUseExactVpa).setOnClickListener(v -> {
            dial = codes.buildViaUpiIdPrompt(payee);
            render();
        });

        render();
    }

    /** Reflects the current {@link #dial} — what gets dialled and what's left to do. */
    private void render() {
        ((TextView) findViewById(R.id.tvDialCode)).setText(dial.code);

        TextView tvWhatsLeft = findViewById(R.id.tvWhatsLeft);
        View clipboardCard = findViewById(R.id.clipboardCard);
        View derivedCard = findViewById(R.id.derivedCard);

        if (dial.derivedMobile != null) {
            // Fully pre-filled, but addressed by number rather than by the UPI ID
            // that was scanned — say so plainly before any money moves.
            tvWhatsLeft.setText(R.string.only_pin_left);
            derivedCard.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.tvDerivedText))
                    .setText(getString(R.string.paying_by_mobile_detail,
                            dial.derivedMobile, payee));
            clipboardCard.setVisibility(View.GONE);
            return;
        }

        derivedCard.setVisibility(View.GONE);

        if (dial.fullyPrefilled) {
            tvWhatsLeft.setText(R.string.only_pin_left);
            clipboardCard.setVisibility(View.GONE);
        } else if (dial.clipboard != null && !dial.clipboard.isEmpty()) {
            // Payee is a UPI ID — copy it so it's one long-press to paste.
            copyToClipboard(dial.clipboard);
            tvWhatsLeft.setText(R.string.paste_then_pin);
            clipboardCard.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.tvClipboardValue)).setText(dial.clipboard);
        } else {
            tvWhatsLeft.setText(R.string.answer_remaining);
            clipboardCard.setVisibility(View.GONE);
        }
    }

    private void copyToClipboard(String value) {
        if (value == null) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("UPI ID", value));
        }
    }

    /**
     * Starts the payment by dialling the prepared string.
     *
     * A payment to a mobile number is carried complete. A payment to a UPI ID is
     * dialled only to the "Enter UPI ID" prompt, because Android strips the
     * address out of a dial string (see {@link UssdCode#build}).
     */
    private void placeCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
            return;
        }

        dialCode(dial.code);
    }

    /**
     * Uri.fromParts builds an opaque tel: URI, so the string is carried
     * verbatim. Uri.parse would treat everything after '#' as a fragment and
     * drop it, which for a USSD code means losing the terminator.
     */
    private void dialCode(String code) {
        Uri uri = Uri.fromParts("tel", code, null);
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                placeCall();
            } else {
                // Not a dead end — the dialler route still works.
                openDialer(Uri.fromParts("tel", dial.code, null));
            }
        }
    }
}
