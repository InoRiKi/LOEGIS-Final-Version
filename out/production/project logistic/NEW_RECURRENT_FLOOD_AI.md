# New Recurrent Flood AI Replacement

The previous flood-susceptibility AI package has been replaced by the newly supplied Hat Yai recurrent-flood XGBoost model.

## New files in `AI_Model/`

- `hatyai_recurrent_flood_model.pkl` — packaged Python XGBoost classifier.
- `hatyai_recurrent_flood_xgboost.json` — XGBoost model export.
- `hatyai_test_predictions.csv` — spatial test predictions with longitude, latitude, actual class, predicted class, and `flood_probability`.
- `flood_risk_grid.geojson` — spatial probability layer consumed by the Loegis Java/Web application.
- `model_info.json` — model/integration metadata.
- `build_recurrent_flood_geojson.py` — reproducible converter from the supplied prediction CSV to Loegis GeoJSON.

The old files `flood_susceptibility_xgboost.pkl`, `feature_columns.pkl`, `flood_training_dataset.csv`, and `snapshots_used.csv` were removed from the runtime AI folder.

## Model characteristics

The supplied model package identifies the classifier inputs as:

- `elevation`
- `slope`

The bundled model uses an XGBoost classifier with 400 estimators and a classification threshold of 0.5.

## How Loegis now uses the new AI

The existing Loegis routing integration is intentionally preserved:

1. The AI page loads `AI_Model/flood_risk_grid.geojson`.
2. The displayed risk value now comes from the new model's `flood_probability` output.
3. Probabilities >= 25% are represented as spatial risk cells.
4. `FloodRiskRoadMapper` maps those cells onto road edges.
5. `RiskAwareRouter` uses the new road-risk values for Transport and Rescue routing.

This means the AI layer, road-risk mapping, field reports, transport routing, and multi-point rescue workflow continue to work without changing their APIs.

## Important implementation note

The current Java application still consumes a **precomputed spatial probability layer** rather than executing XGBoost inference inside the JVM. The new XGBoost `.pkl` and `.json` model files are bundled as the replacement model artifacts, while the supplied `hatyai_test_predictions.csv` is the spatial prediction output used for the current presentation/runtime.

A future fully live version would require a full Hat Yai elevation/slope input raster (or equivalent feature grid) so the new model can generate probabilities for every current map cell on demand.

## Verification performed

Using the current Hat Yai road graph and a 25% AI-risk threshold:

- Prediction rows supplied: 79,175
- Risk cells >= 25%: 10,383
- Road edges receiving AI risk in the integration test: 6,652
- Java source compilation: passed
- JavaScript syntax check: passed
- `/api/predictFloodRisk`: returned the new `hatyai_recurrent_flood_risk` FeatureCollection
- `/api/applyAiFloodRisk`: applied the new recurrent-flood risk to routing successfully
