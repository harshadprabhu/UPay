package com.urgentpay.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Confirmation screen: shows the parsed recipient, lets the user set/verify the
 * amount, then launches the offline USSD session (or an IVR call).
 */
public class PaymentActivity extends AppCompatActivity {

    public static final String EXTRA_VPA = "extra_vpa";
    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_AMOUNT = "extra_amount";

    private String vpa;
    private EditText etAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        vpa = getIntent().getStringExtra(EXTRA_VPA);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        String amount = getIntent().getStringExtra(EXTRA_AMOUNT);

        TextView tvName = findViewById(R.id.tvPayeeName);
        TextView tvVpa = findViewById(R.id.tvPayeeVpa);
        etAmount = findViewById(R.id.etAmount);

        tvName.setText(TextUtils.isEmpty(name) ? vpa : name);
        tvVpa.setText(vpa);
        if (!TextUtils.isEmpty(amount)) {
            etAmount.setText(amount);
        }

        findViewById(R.id.btnPayUssd).setOnClickListener(v -> payViaUssd());
        findViewById(R.id.btnPayIvr).setOnClickListener(v -> payViaIvr());
    }

    private String amount() {
        return etAmount.getText().toString().trim();
    }

    private boolean amountValid() {
        String a = amount();
        if (TextUtils.isEmpty(a)) return false;
        try {
            return Double.parseDouble(a) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void payViaUssd() {
        if (!amountValid()) {
            Toast.makeText(this, "Enter an amount first", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, UssdSessionActivity.class);
        i.putExtra(UssdSessionActivity.EXTRA_VPA, vpa);
        i.putExtra(UssdSessionActivity.EXTRA_AMOUNT, amount());
        startActivity(i);
    }

    private void payViaIvr() {
        // IVR is a voice call to NPCI's 123PAY line — the user follows the
        // spoken prompts. Open the dialer pre-filled; no internet used.
        String num = getString(R.string.ivr_number);
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + num));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_dialer, Toast.LENGTH_LONG).show();
        }
    }
}
