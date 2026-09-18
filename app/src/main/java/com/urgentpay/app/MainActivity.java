package com.urgentpay.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

/**
 * Home screen. Two ways in: scan a UPI QR (offline) or type a UPI ID / mobile
 * number. Both routes end in {@link PaymentActivity}.
 */
public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    return; // user cancelled
                }
                handleScanned(result.getContents());
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnScan).setOnClickListener(v -> launchScanner());
        findViewById(R.id.btnManual).setOnClickListener(v -> showManualEntry());

        bindSteps();
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

    private void launchScanner() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.scan_prompt));
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        scanLauncher.launch(options);
    }

    private void handleScanned(String contents) {
        UpiUri upi = UpiUri.parse(contents);
        if (upi == null) {
            Toast.makeText(this, R.string.not_upi_qr, Toast.LENGTH_LONG).show();
            return;
        }
        openPayment(upi);
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
