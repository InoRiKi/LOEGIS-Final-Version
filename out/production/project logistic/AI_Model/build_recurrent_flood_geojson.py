from pathlib import Path
import json
import pandas as pd

BASE = Path(__file__).resolve().parent
INPUT = BASE / "hatyai_test_predictions.csv"
OUTPUT = BASE / "flood_risk_grid.geojson"
MIN_RISK = 0.25
HALF_CELL_DEG = 0.00045  # approximately 50 m half-width around Hat Yai


def main():
    df = pd.read_csv(INPUT)
    required = {"longitude", "latitude", "prediction", "actual", "flood_probability"}
    missing = required.difference(df.columns)
    if missing:
        raise ValueError(f"Missing columns: {sorted(missing)}")

    risk = df[df["flood_probability"] >= MIN_RISK].copy()

    with OUTPUT.open("w", encoding="utf-8") as f:
        f.write('{"type": "FeatureCollection", "name": "hatyai_recurrent_flood_risk", '
                '"model": "hatyai_recurrent_flood_xgboost", "features": [\n')

        first = True
        for row in risk.itertuples(index=False):
            lon = float(row.longitude)
            lat = float(row.latitude)
            h = HALF_CELL_DEG

            feature = {
                "type": "Feature",
                "properties": {
                    "risk_probability": round(float(row.flood_probability), 8),
                    "risk_percent": round(float(row.flood_probability) * 100.0, 4),
                    "prediction": int(row.prediction),
                    "actual": int(row.actual),
                    "model": "recurrent_flood_xgboost",
                    "features": "elevation,slope",
                },
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[
                        [lon - h, lat - h],
                        [lon + h, lat - h],
                        [lon + h, lat + h],
                        [lon - h, lat + h],
                        [lon - h, lat - h],
                    ]],
                },
            }

            if not first:
                f.write(",\n")
            f.write(json.dumps(feature, ensure_ascii=False))
            first = False

        f.write("\n]}")

    print(f"Generated {OUTPUT}")
    print(f"Total prediction rows: {len(df):,}")
    print(f"Risk cells >= {MIN_RISK * 100:.0f}%: {len(risk):,}")


if __name__ == "__main__":
    main()
