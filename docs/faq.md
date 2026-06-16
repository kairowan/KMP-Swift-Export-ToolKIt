# FAQ

## Does this replace SKIE or KMP-NativeCoroutines?

No. This project is intentionally narrower. It focuses on packaging and release automation for SwiftPM and XCFramework distribution.

## Does it support CocoaPods?

Not in the MVP. The first version stays focused on SwiftPM binary target publishing.

## Does it generate Swift wrappers?

No. The plugin assumes your KMP framework export is already configured and focuses on the release pipeline around it.

## Can I skip GitHub publishing during local development?

Yes.

```kotlin
kmpApplePackager {
    publishRelease.set(false)
    validatePackage.set(false)
}
```

## Which Apple targets are expected?

The MVP defaults to `iosArm64` and `iosSimulatorArm64`, but the release pipeline is driven by the generated XCFramework rather than by target introspection.

