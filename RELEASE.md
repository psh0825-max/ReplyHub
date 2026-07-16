# Release signing

ReplyHub never stores release credentials in the repository. Set these environment variables before building:

```powershell
$env:REPLYHUB_KEYSTORE_PATH = "C:\secure\replyhub-upload.jks"
$env:REPLYHUB_STORE_PASSWORD = "..."
$env:REPLYHUB_KEY_ALIAS = "replyhub"
$env:REPLYHUB_KEY_PASSWORD = "..."
.\gradlew.bat clean assembleRelease
```

The signed APK is written to `app/build/outputs/apk/release/app-release.apk`. When the variables are absent, Gradle still produces an unsigned release APK for local inspection. Keep the keystore and passwords outside this repository and back them up securely.
