package com.urgentpay.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * UPay's QR scanner.
 *
 * The scanner library's bundled CaptureActivity is declared
 * {@code screenOrientation="sensorLandscape"} in the library manifest, which an
 * app cannot override — so it always opened sideways. This activity drives the
 * camera directly and is pinned to portrait, which also lets the chrome match
 * the rest of the app instead of using the library's stock screen.
 *
 * Decoding is entirely on-device; nothing is uploaded and no network is used.
 */
public class ScannerActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT = "extra_result";
    /** Set when the user chose to type the ID instead of scanning. */
    public static final String EXTRA_MANUAL = "extra_manual";

    private static final int REQ_CAMERA = 31;

    private BarcodeView barcodeView;
    private boolean torchOn = false;
    /** Guards against a second result arriving while we're already finishing. */
    private boolean handled = false;

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) decodeImage(uri);
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        barcodeView = findViewById(R.id.barcodeView);
        barcodeView.setDecoderFactory(new DefaultDecoderFactory(
                Arrays.asList(BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX,
                        BarcodeFormat.AZTEC, BarcodeFormat.PDF_417)));

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnTorch).setOnClickListener(v -> toggleTorch());
        findViewById(R.id.btnGallery).setOnClickListener(v -> pickImage.launch("image/*"));
        findViewById(R.id.btnManualFromScan).setOnClickListener(v -> {
            Intent data = new Intent();
            data.putExtra(EXTRA_MANUAL, true);
            setResult(RESULT_OK, data);
            finish();
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void toggleTorch() {
        torchOn = !torchOn;
        try {
            barcodeView.setTorch(torchOn);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_torch, Toast.LENGTH_SHORT).show();
            torchOn = false;
        }
    }

    private void startScanning() {
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (handled || result.getText() == null) return;
                deliver(result.getText());
            }
        });
        barcodeView.resume();
    }

    /** Decodes a QR out of a picture the user picked, for a code that won't scan live. */
    private void decodeImage(Uri uri) {
        try {
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bitmap = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(getContentResolver(), uri),
                        (decoder, info, source) -> {
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                            decoder.setMutableRequired(true);
                        });
            } else {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }
            if (bitmap == null) {
                Toast.makeText(this, R.string.image_unreadable, Toast.LENGTH_LONG).show();
                return;
            }

            int w = bitmap.getWidth(), h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

            BinaryBitmap binary = new BinaryBitmap(
                    new HybridBinarizer(new RGBLuminanceSource(w, h, pixels)));

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            List<BarcodeFormat> formats = new ArrayList<>(Arrays.asList(
                    BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.AZTEC, BarcodeFormat.PDF_417));
            hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);

            Result result = new MultiFormatReader().decode(binary, hints);
            deliver(result.getText());
        } catch (Exception e) {
            // Includes NotFoundException when the picture holds no readable code.
            Toast.makeText(this, R.string.no_qr_in_image, Toast.LENGTH_LONG).show();
        }
    }

    private void deliver(String text) {
        if (handled) return;
        handled = true;
        barcodeView.pause();

        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT, text);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handled = false;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScanning();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Toast.makeText(this, R.string.camera_needed, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
