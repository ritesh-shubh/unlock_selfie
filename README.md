# unlock_selfie
This app takes a selfie from the front camera every time the phone is unlocked, with an option while setting up for the directory to save in and to take the photo every time an incorrect password is entered or every time the phone is unlocked.

## Build a Windows `.exe` helper
You can generate a small Windows executable from this repo with:

```powershell
powershell -ExecutionPolicy Bypass -File .\make_exe.ps1
```

The generated file is `.\dist\UnlockSelfie.exe`.

Note: this project is an Android app, so the installable mobile artifact is still an APK (`app-release.apk`).
