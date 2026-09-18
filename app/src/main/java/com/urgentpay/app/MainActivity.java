package com.urgentpay.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

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
        findViewById(R.id.btnAddPayee).setOnClickListener(v -> showAddPayee(null));

        bindSteps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindPayees();
    }

    /** Renders the saved payees, each payable in one shot. */
    private void bindPayees() {
        LinearLayout container = findViewById(R.id.payeeList);
        container.removeAllViews();

        List<Payee> payees = new PayeeStore(this).all();
        findViewById(R.id.payeeEmpty).setVisibility(payees.isEmpty() ? View.VISIBLE : View.GONE);

        for (final Payee p : payees) {
            View row = getLayoutInflater().inflate(R.layout.item_payee, container, false);
            String initial = p.nickname.isEmpty() ? "?" : p.nickname.substring(0, 1).toUpperCase();
            ((TextView) row.findViewById(R.id.payeeInitial)).setText(initial);
            ((TextView) row.findViewById(R.id.payeeName)).setText(p.nickname);
            ((TextView) row.findViewById(R.id.payeeVpa)).setText(p.vpa);
            ((TextView) row.findViewById(R.id.payeeBadge)).setText("#" + p.index);

            ((ViewGroup.MarginLayoutParams) row.getLayoutParams()).topMargin =
                    (int) (8 * getResources().getDisplayMetrics().density);

            row.setOnClickListener(v -> openPaymentForPayee(p));
            row.setOnLongClickListener(v -> {
                confirmRemovePayee(p);
                return true;
            });
            container.addView(row);
        }
    }

    private void confirmRemovePayee(Payee p) {
        new AlertDialog.Builder(this)
                .setTitle(p.nickname)
                .setMessage(getString(R.string.remove_payee_confirm, p.nickname))
                .setPositiveButton(R.string.remove, (d, w) -> {
                    new PayeeStore(this).remove(p.vpa);
                    bindPayees();
                })
                .setNeutralButton(R.string.edit, (d, w) -> showAddPayee(p))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Records a payee the user has already saved at their bank, together with
     * the number the bank listed them under — that number is what makes a
     * one-shot payment to a UPI ID possible.
     */
    private void showAddPayee(final Payee existing) {
        View form = getLayoutInflater().inflate(R.layout.dialog_add_payee, null);
        EditText etNickname = form.findViewById(R.id.etNickname);
        EditText etVpa = form.findViewById(R.id.etVpa);
        EditText etIndex = form.findViewById(R.id.etIndex);

        if (existing != null) {
            etNickname.setText(existing.nickname);
            etVpa.setText(existing.vpa);
            etIndex.setText(existing.index);
        }

        form.findViewById(R.id.btnShowBankList).setOnClickListener(v -> {
            String code = new UssdCode(this).listBeneficiariesCode();
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", code, null)));
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_payee_title)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String nick = etNickname.getText().toString().trim();
                    String vpa = etVpa.getText().toString().trim();
                    String index = etIndex.getText().toString().trim();

                    if (nick.isEmpty() || vpa.isEmpty() || !index.matches("\\d{1,3}")) {
                        Toast.makeText(this, R.string.payee_incomplete, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (existing != null && !existing.vpa.equalsIgnoreCase(vpa)) {
                        new PayeeStore(this).remove(existing.vpa);
                    }
                    new PayeeStore(this).save(new Payee(nick, vpa, index));
                    bindPayees();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openPaymentForPayee(Payee p) {
        Intent i = new Intent(this, PaymentActivity.class);
        i.putExtra(PaymentActivity.EXTRA_VPA, p.vpa);
        i.putExtra(PaymentActivity.EXTRA_NAME, p.nickname);
        i.putExtra(PaymentActivity.EXTRA_BENEFICIARY_INDEX, p.index);
        startActivity(i);
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
