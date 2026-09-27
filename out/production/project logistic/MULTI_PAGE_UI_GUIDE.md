# Loegis Multi-Page UI

## Goal
The previous UI placed route planning, rescue planning, AI flood risk, and field reports in one long sidebar. This update separates those workflows into focused pages while keeping a single persistent map and shared application state.

## Pages

### 1. Overview
Used only for initial/system actions:
- Load the GraphML map
- View map status
- Sync current situation
- Clear the entire simulation
- See the recommended workflow

### 2. Transport
Opens the route-planning workspace in Transport mode:
- Select start
- Select destination
- Select Fastest / Balanced / Safest
- Calculate route
- View route distance and risk result

### 3. Rescue
Uses the same route workspace but switches it into Rescue mode:
- Select rescue start/depot
- Add multiple rescue points
- Enter victims and priority per point
- Configure fleet/capacity only when needed
- Run multi-stop / multi-trip rescue planning

### 4. AI Risk
Contains only AI flood-risk controls:
- Run XGBoost flood-risk visualization
- Apply AI risk to the road network
- Clear prediction
- View risk legend/status

### 5. Field Report
Contains only confirmed/manual flood input:
- Note
- Flood level
- Radius
- Select map position
- Save/Clear field reports

## Why this is still one HTML app
The interface behaves like separate pages, but it is implemented as an SPA inside `index.html`.
This is intentional: the Leaflet map, selected nodes, rescue requests, AI risk, and field reports remain in memory while users switch workflows.

If separate HTML pages were used, state would need to be serialized/reloaded every time the user changed pages, which would make the prototype more fragile.

## Main code changes

### `webapp/index.html`
Reorganized the sidebar into:
- `homePage`
- `routePage`
- `aiPage`
- `fieldPage`

The top navigation has five user-facing entries:
- Overview
- Transport
- Rescue
- AI Risk
- Field Report

Transport and Rescue reuse the same route workspace so existing route/rescue IDs and backend APIs remain stable.

### `webapp/app.js`
Added:
- `openAppPage(pageId, activeButton)`
- `updateRoutePageHeader()`
- navigation event handlers
- Thai/English translations for all new page labels and helper text

Transport and Rescue navigation still call the original `changeTravelMode()` logic, so routing behavior is unchanged.

### `webapp/style.css`
Added a new multi-page shell:
- compact page navigation
- one active workflow at a time
- scrollable content workspace
- overview cards
- responsive mobile sidebar behavior

## Preserved behavior
The following existing functionality remains connected:
- AI risk -> road network -> risk-aware routing
- Field Report overrides
- Transport routing
- Multi-point rescue
- Multi-stop rescue loops
- Per-point victim count
- Route direction arrows / route labels
- Thai / English switch

## Verification
- JavaScript syntax check: PASS
- Java compile with project libraries: PASS
- Server static UI load: PASS
- API map load: PASS
- Hat Yai GraphML: 17,164 nodes / 78,738 edges
