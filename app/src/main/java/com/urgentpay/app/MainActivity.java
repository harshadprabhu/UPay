package com.urgentpay.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Home screen. Two ways in: scan a UPI QR (offline) or type a UPI ID / mobile
 * number. Both routes end in {@link PaymentActivity}.
 */
public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<Intent> scanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                            return; // cancelled
                        }
                        Intent data = result.getData();
                        if (data.getBooleanExtra(ScannerActivity.EXTRA_MANUAL, false)) {
                            showManualEntry();
                            return;
                        }
                        String contents = data.getStringExtra(ScannerActivity.EXTRA_RESULT);
                        if (contents != null) handleScanned(contents);
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnScan).setOnClickListener(v -> launchScanner());
        findViewById(R.id.btnManual).setOnClickListener(v -> showManualEntry());
        findViewById(R.id.btnBankSettings).setOnClickListener(v -> showBankMenuSettings());

        bindSteps();
    }

    /**
     * Lets the user correct the *99# option numbers. Banks number their menus
     * differently — one real HDFC menu lists 1, 3, 4, 5 and skips 2 — so these
     * cannot be safely hard-coded for everyone.
     */
    private void showBankMenuSettings() {
        View form = getLayoutInflater().inflate(R.layout.dialog_bank_menu, null);
        EditText etSend = form.findViewById(R.id.etSendMoney);
        EditText etMobile = form.findViewById(R.id.etByMobile);
        EditText etUpi = form.findViewById(R.id.etByUpiId);

        UssdCode current = new UssdCode(this);
        etSend.setText(current.getSendMoney());
        etMobile.setText(current.getByMobile());
        etUpi.setText(current.getByUpiId());

        new AlertDialog.Builder(this)
                .setTitle(R.string.bank_menu_title)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String s = etSend.getText().toString().trim();
                    String m = etMobile.getText().toString().trim();
                    String u = etUpi.getText().toString().trim();
                    if (s.isEmpty() || m.isEmpty() || u.isEmpty()) return;
                    UssdCode.save(this, s, m, u);
                    Toast.makeText(this, R.string.bank_menu_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void bindSteps() {
        bindStep(R.id.step1, "1", getString(R.string.step1_title), getString(R.string.step1_desc));
        bindStep(R.id.step2, "2", getString(R.string.step2_title), getString(R.string.step2_desc));
        bindStep(R.id.step3, "3", getString(R.string.step3_title), getString(R.string.step3_desc));
    }

    private void bindStep(int containerId, String num, String title, String desc) {
        View c = findViewById(containerId);
        ((TextView) c.findViewById(R.id.stepNum)).setText(num);
        ((TextView) c.findViewById(R.id.stepTitle)).setText(title);
        ((TextView) c.findViewById(R.id.stepDesc)).setText(desc);
    }

    /**
     * Opens UPay's own scanner, which handles its own camera permission and,
     * unlike the library's bundled one, stays in portrait.
     */
    private void launchScanner() {
        scanLauncher.launch(new Intent(this, ScannerActivity.class));
    }

    private void handleScanned(String contents) {
        UpiUri upi = UpiUri.parse(contents);
        if (upi == null) {
            // Show what was actually in the code — a silent "didn't work" makes
            // an unsupported QR format impossible to report or diagnose.
            showUnreadableQr(contents);
            return;
        }
        openPayment(upi);
    }

    private void showUnreadableQr(String contents) {
        String preview = contents == null ? "(empty)" : contents.trim();
        if (preview.length() > 300) preview = preview.substring(0, 300) + "…";

        new AlertDialog.Builder(this)
                .setTitle(R.string.not_upi_qr)
                .setMessage(getString(R.string.not_upi_qr_detail, preview))
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.scan_again, (d, w) -> launchScanner())
                .show();
    }

    private void showManualEntry() {
        final EditText input = new EditText(this);
        input.setHint(R.string.manual_vpa_hint);
        input.setSingleLine(true);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);

        new AlertDialog.Builder(this)
                .setTitle(R.string.manual_title)
                .setView(input, pad, pad / 2, pad, 0)
                .setPositiveButton(R.string.manual_continue, (d, w) -> {
                    UpiUri upi = UpiUri.parse(input.getText().toString());
                    if (upi == null) {
                        Toast.makeText(this, R.string.invalid_vpa, Toast.LENGTH_LONG).show();
                        return;
                    }
                    openPayment(upi);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openPayment(UpiUri upi) {
        Intent i = new Intent(this, PaymentActivity.class);
        i.putExtra(PaymentActivity.EXTRA_VPA, upi.vpa);
        i.putExtra(PaymentActivity.EXTRA_NAME, upi.name);
        i.putExtra(PaymentActivity.EXTRA_AMOUNT, upi.amount);
        startActivity(i);
    }
}
