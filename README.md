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
- ⚡ **The bank menus are skipped.** Instead of walking the `*99#` menu one slow step at
  a time, UPay dials a string with the answers already in it:

  ```
  *99*1*1*9876543210*500*1#
     │ │  │          │   └── skip the remark
     │ │  │          └────── amount
     │ │  └───────────────── payee's mobile number
     │ └──────────────────── send by mobile number
     └────────────────────── Send Money
  ```

  Each menu step is a separate round trip to the network, so collapsing them removes
  most of the typing **and** most of the waiting. Only the UPI PIN prompt is left.
- 📋 **Clipboard assist for UPI IDs** — a UPI ID contains `@`, `.` and `-`, which a USSD
  dial string can't reliably carry. For those, UPay dials straight to the "Enter UPI ID"
  prompt and copies the address so it's one long-press to paste.
- 🏦 **Adjustable menu numbers** — banks number their menus differently (a real HDFC menu
  lists `1, 3, 4, 5` and skips `2`), so the option digits are editable in Settings.
- 📞 **IVR fallback** — pay by calling NPCI's UPI 123PAY voice line.
- 🔒 **Your UPI PIN is never handled by UPay.** It's typed on the phone's own `*99#`
  screen. The app only dials — it has no way to see your PIN.
- 🚫 **No `INTERNET` permission.** Works fully offline by design.
- 🙌 **No signup, no account.** Download and use.

## Honest limitations

- **The PIN prompt always comes from your bank**, on the system's own USSD screen. That
  is deliberate and is the right place for it — no app should ever collect a UPI PIN.
- **How deep the pre-filled string goes can vary** by bank and telecom operator. If the
  network stops partway, the remaining prompts simply appear as normal and you answer
  them — you still skip everything up to that point.
- **UPI IDs can't go inside the dial string**, hence the clipboard-paste step for them.
  Payments to a **mobile number** are the ones that pre-fill completely.
- **IVR cannot be turned into in-app UI.** IVR is a voice call; Android gives apps no
  access to live call audio, so IVR is a real phone call the user listens to.
- **Fully embedding the payment** (no system screen at all) requires being an authorised
  NPCI **TPAP** with a PSP bank partnership — a licensing requirement, not a technical
  one. No sideloaded app can do it.

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
