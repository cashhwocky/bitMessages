# bitMessages

bitMessages. For bitDroid.

SMS and MMS texting for bitDroid (Pixel 10, Android 17). Kotlin + Jetpack Compose, no RCS, no servers:
everything goes through the phone's own Telephony stack.

## Features

- Works as the **default SMS app** (`ROLE_SMS`): `SMS_DELIVER`, `WAP_PUSH_DELIVER` (MMS), respond-via-message service, and `SEND`/`SENDTO` for `sms:` `smsto:` `mms:` `mmsto:`
- Reads and writes the system Telephony provider, so your existing texts show up and new ones are saved there
- SMS, including long multipart texts, with sent / delivered / failed states and tap-to-retry
- MMS (pictures, group texts) through `SmsManager.sendMultimediaMessage` / `downloadMultimediaMessage`, with a built-in PDU composer and parser (`mms/Pdu.kt`, `mms/MmsPdu.kt`)
- Group conversations, contact names and photos, search, delete, mark as read, block numbers (system block list)
- Notifications with inline reply and mark-as-read, one notification channel per conversation
- Direct Boot aware: texts that reach the app before the first unlock are kept in device-protected storage, notified, and imported after unlock
- Dual SIM / eSIM: pick the SIM per conversation from the composer

## Install

Grab `bitMessages-x.y.z-release.apk` from [Releases](../../releases), install it, open it, and tap **Set as default**.

## Build

CI builds every push; a tag like `v0.1.0` publishes a release with `bitMessages-0.1.0-release.apk`.
The tag must match `versionName` in `app/build.gradle.kts`.

Release signing (optional) uses these repo secrets, otherwise the release APK is debug-signed:
`BITMESSAGES_KEYSTORE_B64`, `BITMESSAGES_KEYSTORE_PASSWORD`, `BITMESSAGES_KEY_ALIAS`, `BITMESSAGES_KEY_PASSWORD`.

## Shipping in bitDroid

See `rom/`: an `Android.bp` prebuilt module and a `config_defaultSms` overlay.
