# Execution Plan: Resolving AR Launch `NoSuchMethodError` Crash

**Document:** `docs/AR/Implementation/Phases/Phase 8/Phase_8_Crash_Fix_Execution_Plan.md`  
**Reference:** [`samsung-SM-S908E-Android-13_2026-08-30_083920.logcat`](file:///C:/Users/youss/Downloads/MallAR-main%2022/MallAR-main/docs/AR/Implementation/Bugs%20and%20Issues%20reported/LOGCAT%20FILES%20TEST/Logcat%20of%20phase%208/samsung-SM-S908E-Android-13_2026-08-30_083920.logcat)  
**Target Device:** Samsung Galaxy S22 Ultra (`SM-S908E`, Android 13)

---

## 1. Problem Diagnosis & Logcat Root Cause

From the latest logcat capture (`samsung-SM-S908E-Android-13_2026-08-30_083920.logcat`), the exact fatal runtime exception occurred when tapping **AR** to launch navigation:

```text
[ERROR] AndroidRuntime: Caused by: java.lang.NoSuchMethodError: No direct method <init>(IDDILcom/example/mallar/data/AStarDirection;ZZ)V in class Lcom/example/mallar/ar/model/RouteNodeMetadata; or its super classes (declaration of 'com.example.mallar.ar.model.RouteNodeMetadata' appears in /data/app/~~u0xjN0aidMcKzG0EYCtGEw==/com.example.mallar-P38RMJdnOi6EibEwUSEs0g==/base.apk!classes8.dex)
	at com.example.mallar.ar.RoutePathLayer.refreshMetadata(RoutePathLayer.kt:95)
	at com.example.mallar.ar.RoutePathLayer.<init>(RoutePathLayer.kt:31)
	at com.example.mallar.ui.navigation.UnifiedNavigationViewModel.<init>(UnifiedNavigationViewModel.kt:46)
```

### Why This Happened:
1. In Phase 8, a new property `isFloorTransition: Boolean = false` was added to `RouteNodeMetadata`.
2. When compiled incrementally by Gradle / Android Studio, `RoutePathLayer.kt` was recompiled targeting the 7-parameter constructor:
   $$\text{Signature: }\texttt{<init>(IDDILcom/example/mallar/data/AStarDirection;ZZ)V}$$
3. However, on the physical device, Android Studio's deployment cache (`code_cache/.overlay/base.apk/`) or an incremental DEX slice retained the previous 6-parameter constructor signature:
   $$\text{Signature: }\texttt{<init>(IDDILcom/example/mallar/data/AStarDirection;Z)V}$$
4. When `UnifiedNavigationViewModel` instantiated `RoutePathLayer`, the Android Runtime (ART) threw `java.lang.NoSuchMethodError` because the expected 7-parameter constructor was missing in the loaded `classes8.dex`.

---

## 2. Proposed Structural Changes

### 2.1 Backward-Compatible Constructor Overloads in `ArDataModels.kt`
Add secondary constructors and explicit `@JvmOverloads` to `RouteNodeMetadata` and `NavigationSessionSnapshot` in [`app/src/main/java/com/example/mallar/ar/model/ArDataModels.kt`](file:///C:/Users/youss/Downloads/MallAR-main%2022/MallAR-main/app/src/main/java/com/example/mallar/ar/model/ArDataModels.kt):

```kotlin
data class RouteNodeMetadata @JvmOverloads constructor(
    val nodeId: Int,
    val x: Double,
    val y: Double,
    val floor: Int,
    val direction: AStarDirection,
    val isDestination: Boolean,
    val isFloorTransition: Boolean = false
) {
    // Explicit secondary constructor guaranteeing ABI compatibility with 6-arg callers
    constructor(
        nodeId: Int,
        x: Double,
        y: Double,
        floor: Int,
        direction: AStarDirection,
        isDestination: Boolean
    ) : this(nodeId, x, y, floor, direction, isDestination, false)
}
```

### 2.2 Clean Build & DEX Cache Invalidation
Execute a full `./gradlew.bat clean :app:assembleDebug` to purge all intermediate build artifacts, ensuring all classes are freshly compiled into a unified DEX package without incremental stale slices.

---

## 3. Verification & Execution Results

### 3.1 Automated Verification:
- **Unit Test Suite:** Executed `./gradlew.bat clean :app:testDebugUnitTest` — **26/26 tasks executed, 50/50 unit tests passing.**
- **Clean Full Build:** Executed `./gradlew.bat assembleDebug` — **BUILD SUCCESSFUL in 37s.**
- **Output Artifact:** `app/build/outputs/apk/debug/app-debug.apk`

---

## 4. On-Device Verification Instructions (User Action)

> [!IMPORTANT]
> **Step 1: Clean Uninstall Old App**  
> On your Samsung Galaxy S22 Ultra, **uninstall the previous version of MallAR completely** (hold app icon $\rightarrow$ *Uninstall*). This purges Android's ART `oat`/`odex` and Studio overlay cache (`code_cache/.overlay/base.apk/`).

> [!NOTE]
> **Step 2: Install Fresh APK**  
> Install the freshly assembled build: `app/build/outputs/apk/debug/app-debug.apk`.

> [!TIP]
> **Step 3: Launch Navigation & Observe**  
> 1. Open the app and scan a store logo (or choose start/end stores).
> 2. Tap **AR** to start navigation.
> 3. Verify that the app transitions smoothly without crashing, the camera opens, and 3D guidance anchors render on the floor.
