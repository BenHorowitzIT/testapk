# VelvetBox

Android app sandbox with a built-in XAPK installer and file browser (Android 9+).

- Apps you install go **inside VelvetBox**, never onto the phone itself.
- XAPK / APKS files: split APKs are merged and signed, OBB data goes into the app's own sandboxed `Android/obb` folder.
- File browser with APK/XAPK and OBB filters, plus a view of the sandbox's own storage.

## Build
Push this folder to GitHub. The **Build APK** workflow (Actions tab) downloads the sandbox
engine and split-APK merger, builds the app, and uploads `VelvetBox-apk` as an artifact.

## Credits (Apache-2.0)
- Sandbox engine: [NewBlackbox](https://github.com/ALEX5402/NewBlackbox) (BlackBox)
- Split APK merging: [ARSCLib / APKEditor](https://github.com/REAndroid/APKEditor)
- APK signing: apksig (Android Open Source Project)
