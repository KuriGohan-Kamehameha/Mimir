# Mimir Prerelease 0.0.5-beta

Version metadata:
- versionName: 0.0.5-beta
- versionCode: 5
- applicationId: com.mimir.translate

## Included artifacts

- mimir-0.0.5-beta-debug.apk (debug build, installable)
- mimir-0.0.5-beta-release.apk (release/normal build, signed with local debug keystore for install testing)
- mimir-0.0.5-beta-release-unsigned.apk (release/normal build, unsigned)
- mimir-0.0.5-beta-source.zip (source snapshot with Gradle wrapper and project files)
- SHA256SUMS.txt (checksums)

## Build requirements

- OpenJDK 17
- Android SDK + required Build Tools
- Gradle wrapper included (./gradlew)

## Rebuild commands

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug :app:assembleRelease
```

Fresh build output paths:
- app/build/outputs/apk/debug/app-debug.apk
- app/build/outputs/apk/release/app-release-unsigned.apk

## Install commands

```bash
adb install -r mimir-0.0.5-beta-debug.apk
adb install -r mimir-0.0.5-beta-release.apk
```

## Signing note

The normal release APK in this prerelease bundle is signed with the local debug keystore for convenience testing.
Use your production keystore to create a production release APK.
