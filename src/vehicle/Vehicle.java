package src.vehicle;

import src.model.Edge;
import src.model.Node;

public abstract class Vehicle {

    private final String id;
    private final double capacity;
    private double currentLoad;
    private Node startNode;
    private final VehicleType type;

    protected Vehicle(
            String id,
            double capacity,
            Node startNode,
            VehicleType type
    ) {
        this.id = id;
        this.capacity = capacity;
        this.startNode = startNode;
        this.type = type;
        this.currentLoad = 0.0;
    }

    public abstract boolean canPass(Edge edge);

    public abstract boolean allowsReverseEdge();

    public String getId() {
        return id;
    }

    public double getCapacity() {
        return capacity;
    }

    public double getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(double currentLoad) {
        this.currentLoad = currentLoad;
    }

    public Node getStartNode() {
        return startNode;
    }

    public void setStartNode(Node startNode) {
        this.startNode = startNode;
    }

    public VehicleType getType() {
        return type;
    }
}
