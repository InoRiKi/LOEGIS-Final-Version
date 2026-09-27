# Route Visual Updates

This update focuses on making route visualization easier to understand.

## What changed

1. **Direction arrows on route lines**
   - Added arrow markers on route polylines.
   - Works for both normal transport routes and multi-rescue routes.

2. **Readable route labels on the map**
   - Rescue routes are now labeled as `Route 1`, `Route 2`, etc. / `เส้นทาง 1`, `เส้นทาง 2`.
   - Transport mode shows `Transport Route` / `เส้นทางขนส่ง`.
   - This replaces unclear labels like `R-01`, `R-02` on the map UI.

3. **Larger and clearer result panel**
   - Rescue result panel is now larger.
   - Bigger typography.
   - Clear summary at the top.
   - Each route card now uses a large badge and larger text.

4. **Bigger route result box**
   - The normal route summary box in the sidebar was also enlarged for readability.

## Main frontend logic added

### New helpers in `webapp/app.js`
- `buildPathLatLngs(...)`
- `createArrowIcon(...)`
- `addRouteArrowMarkers(...)`
- `addRouteLabel(...)`

### Updated functions
- `renderRoute(state)` now:
  - builds full route paths
  - draws one clean polyline per route
  - adds direction arrows
  - adds route labels on the map

- `updateRescuePlannerUI(state)` now:
  - shows a larger rescue summary
  - replaces vehicle-id style display with human-readable route labels
  - uses larger route cards

## Styling changes
Updated `webapp/style.css` for:
- route arrows
- map route labels
- bigger rescue result panel
- larger result cards and summary
