# Ben's Sandbox

Android app sandbox (Android 9+): apps you install go inside Ben's Sandbox, never onto the phone itself.

- Install regular APK files into the sandbox (pick a file, or from the detected list)
- Open, disable, clear data, uninstall sandboxed apps
- "Sandbox app" home-screen widget: long-press the Ben's Sandbox icon, Widgets, pick an app

## Build
Push to GitHub. The **Build APK** workflow downloads the sandbox engine, applies the patches in
`engine-patches/`, builds the app, and uploads `VelvetBox-apk` as an artifact.

## Credits
- Sandbox engine: [NewBlackbox](https://github.com/ALEX5402/NewBlackbox) (BlackBox, Apache-2.0)
