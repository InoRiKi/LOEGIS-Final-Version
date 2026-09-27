package src.routing;

public enum RoutePreference {
    FASTEST(1.0, 0.10),
    BALANCED(1.0, 0.75),
    SAFEST(0.85, 2.50);

    private final double distanceWeight;
    private final double riskWeight;

    RoutePreference(double distanceWeight, double riskWeight) {
        this.distanceWeight = distanceWeight;
        this.riskWeight = riskWeight;
    }

    public double distanceWeight() { return distanceWeight; }
    public double riskWeight() { return riskWeight; }

    public static RoutePreference from(String value) {
        if (value == null) return BALANCED;
        try {
            return RoutePreference.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BALANCED;
        }
    }
}
