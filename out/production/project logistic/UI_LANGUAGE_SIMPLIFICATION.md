# Loegis UI simplification + TH/EN update

## What changed

### 1) Vehicle type selection removed
The UI no longer asks the user to choose DELIVERY_TRUCK / RESCUE_TRUCK / HIGH_WATER_RESCUE.
The app now focuses on the two main operating modes only:
- Transport
- Rescue

The frontend no longer sends a `vehicle` field. Existing backend API defaults are used internally:
- `/api/runRoute` -> DELIVERY_TRUCK
- `/api/runMultiVehicleRescue` -> RESCUE_TRUCK

This keeps the existing routing logic compatible without exposing unnecessary technical choices in the UI.

### 2) Field Report simplified
Removed from the UI:
- Report type
- Reporter / coordinator / source

Field Report now contains only:
- Note
- Flood level
- Radius
- Location

The frontend stores reports as `CONFIRMED_FLOOD` internally. This keeps the existing backend structure compatible while making the workflow simpler.

### 3) Redundant route explanation removed
The route result already shows distance, average risk and maximum risk, so the extra backend explanation line was removed from the visible UI.

### 4) TH / EN coverage expanded
The language button now updates both static and dynamic UI for the newer features, including:
- Transport / Rescue modes
- Risk routing preference
- Multi-point rescue planner
- Per-point victim count dialog
- Rescue queue and rescue result panel
- Rescue trips and assignments
- Field Report
- AI flood prediction controls and status
- Toast messages
- Placeholders, titles and aria labels
- Map tooltips when state is re-rendered

Dynamic text is generated through `t()`, `tf()` and `setDynamicText()` so switching language updates active state rather than only fixed labels.

## Main files changed
- `webapp/index.html`
- `webapp/app.js`

## Validation
- `node --check webapp/app.js` -> PASS
- Java 21 compilation of all `src/**/*.java` -> PASS
