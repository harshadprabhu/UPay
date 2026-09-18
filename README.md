# UPay — UrgentPay

**Emergency offline UPI payments for Android. No internet, no signup.**

UPay (full name **UrgentPay**) is a lightweight Android app that helps you start a
UPI payment when you have **no mobile data or Wi-Fi** — for example in a low-network
area, when your data pack runs out, or during an outage.

It does this by driving India's **official offline payment rails** — the
`*99#` USSD service (NPCI's National Unified USSD Platform) and **UPI 123PAY IVR** —
which run over the plain telecom network and need no internet connection.

> ⚠️ **What UPay is and isn't.** UPay does **not** process payments itself and is
> **not** a PSP/bank app. Your bank completes the transfer over the `*99#` / IVR
> telecom rails. UPay just makes *starting* one fast in an emergency, with a clean UI.
> It is an independent utility, **not affiliated with NPCI, UPI, BHIM, or any bank**.
> No app can guarantee a payment — availability depends on your bank and SIM.

## Features

- 📷 **Offline QR scan** — read any standard UPI QR code on-device (ZXing, no network)
  and auto-fill the recipient UPI ID and amount. No manual typing.
- ⌨️ **Manual entry** — or type a UPI ID / mobile number yourself.
- ⚡ **In-app `*99#` session** — on Android 8+ the app runs `*99#` in the background via
  `TelephonyManager.sendUssdRequest()` and renders the bank's replies as clean UI
  instead of the raw system dialer overlay.
- 📞 **IVR fallback** — pay by calling NPCI's UPI 123PAY voice line.
- 🔒 **Your UPI PIN is never handled by UPay.** At the approval step the app hands off
  to the phone's **secure NPCI PIN screen** — the only compliant place to type a UPI PIN.
- 🚫 **No `INTERNET` permission.** Works fully offline by design.
- 🙌 **No signup, no account.** Download and use.

## Honest limitations

- **IVR cannot be turned into in-app UI.** IVR is a voice call; Android gives apps no
  access to live call audio, so IVR is a real phone call the user listens to — not
  on-screen buttons.
- **UPI PIN entry is always handed off** to the secure system screen (by design and by
  NPCI rules), never captured in-app.
- **Programmatic `*99#`** requires Android 8+ and carrier support; where unavailable,
  UPay falls back to the secure dialer with `*99#` pre-filled.

## Tech

- Java, `minSdk 24`, `targetSdk 34`, AndroidX + Material 3 (dark "Copper on Cocoa"
  glassmorphism theme).
- Offline QR: `com.journeyapps:zxing-android-embedded`.

## Build

```bash
# Requires Android SDK (platform 34, build-tools 34.0.0) and JDK 17.
./gradlew :app:assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
```

### Signed release

Provide your own keystore (never commit it):

```bash
export UPAY_KEYSTORE=/path/to/your.jks
export UPAY_STORE_PASSWORD=... UPAY_KEY_ALIAS=... UPAY_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

CI (`.github/workflows/android.yml`) builds a debug APK on every push and uploads it as
a workflow artifact.

## Disclaimer

This project is provided as-is for educational and personal use. UPI, BHIM, `*99#`,
123PAY and related marks belong to NPCI. Always verify the recipient before approving a
payment — transfers cannot be reversed.

## License

MIT — see [LICENSE](LICENSE).
