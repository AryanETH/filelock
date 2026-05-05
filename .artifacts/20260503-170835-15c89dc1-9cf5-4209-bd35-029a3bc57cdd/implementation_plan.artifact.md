# Implementation Plan - Mapp Lock Screen Flow and Theme System

Design and implement a clean, smooth, and logical 3-screen flow for the Mapp Lock app, ensuring proper separation between app themes and map layers, and secure interaction via long press.

## Proposed Changes

### Intro Screen Component

#### [IntroScreen.kt](file:///C:/my%20projects/file%20lock/app/src/main/java/com/geovault/ui/IntroScreen.kt)

- Created a new Composable for the app launch screen.
- Displays "Mapp Lock" name and logo.
- Implements a subtle fade and scale animation for the logo.

---

### Main Navigation & State

#### [MainActivity.kt](file:///C:/my%20projects/file%20lock/app/src/main/java/com/geovault/MainActivity.kt)

- Added a 1.5-second splash screen state (`showSplash`).
- Updated `AnimatedContent` to handle the transition from `intro` to the rest of the app with a smooth fade.
- Passed `onToggleSatellite` to `VaultScreen`.

#### [VaultState.kt](file:///C:/my%20projects/file%20lock/app/src/main/java/com/geovault/model/VaultState.kt)

- Added `isSatelliteMode` to the state to track map layers independently.

#### [VaultViewModel.kt](file:///C:/my%20projects/file%20lock/app/src/main/java/com/geovault/ui/VaultViewModel.kt)

- Implemented `toggleSatelliteMode` and persisted it in `SharedPreferences`.
- Initialized `isSatelliteMode` from persisted storage.

---

### Map View & Secure Interaction

#### [VaultScreen.kt](file:///C:/my%20projects/file%20lock/app/src/main/java/com/geovault/ui/VaultScreen.kt)

- **Independent Toggles**: Added a Theme toggle and a Map Layer toggle to the map UI.
- **Theme/Layer Separation**: Ensured map style depends only on `isSatelliteMode`, keeping it independent of the app's light/dark theme.
- **Long Press Unlock**: Replaced double-tap with long-press to trigger the vault unlock if a vault is nearby.
- **Ripple Effect**: Implemented a visual ripple/pulse effect at the touch point on long press using `Animatable` and `Canvas`.
- **Blur & Scale-in**: Applied a blur effect to the map background and a scale-in animation to the lock UI dialog when active.
- **Smooth Content Transition**: Wrapped the switch between Map and Vault Content in an `AnimatedContent` for a smooth reveal animation.

---

## Verification Plan

### Manual Verification

1.  **Launch Flow**:
    - Open the app.
    - Verify the "Mapp Lock" intro screen appears with a logo animation for ~1.5s.
    - Verify it smoothly fades into the Map screen.
2.  **Theme & Layers**:
    - Click the Layer toggle on the map. Verify it switches between Default (light earthy) and Satellite imagery.
    - Click the Theme toggle on the map. Verify the app UI (buttons, text) changes between Light and Dark themes.
    - Verify that changing the Theme does NOT change the Map Layer.
3.  **Secure Interaction**:
    - Long press on the map where a vault is located.
    - Verify a ripple effect appears at the touch point.
    - Verify the background blurs and the Lock UI (PIN/Pattern) scales in.
    - Enter the correct PIN/Pattern and verify a smooth transition to the vault content.
4.  **Performance**:
    - Observe map loading and transitions. Ensure no lag or blocking UI threads.
