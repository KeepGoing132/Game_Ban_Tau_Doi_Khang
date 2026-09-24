package common.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Ship {
    private String id;
    private int size;
    private List<Coordinate> coordinates = new ArrayList<>();
    private Set<Coordinate> hitCoordinates = new HashSet<>();

    public Ship() {
    }

    public Ship(String id, int size, List<Coordinate> coordinates) {
        this.id = id;
        this.size = size;
        this.coordinates = coordinates != null ? coordinates : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<Coordinate> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<Coordinate> coordinates) {
        this.coordinates = coordinates;
    }

    public Set<Coordinate> getHitCoordinates() {
        return hitCoordinates;
    }

    public void setHitCoordinates(Set<Coordinate> hitCoordinates) {
        this.hitCoordinates = hitCoordinates;
    }

    public boolean contains(Coordinate c) {
        if (coordinates == null) return false;
        for (Coordinate shipCoord : coordinates) {
            if (shipCoord.getX() == c.getX() && shipCoord.getY() == c.getY()) {
                return true;
            }
        }
        return false;
    }

    public boolean recordHit(Coordinate c) {
        if (contains(c)) {
            hitCoordinates.add(c);
            return true;
        }
        return false;
    }

    public boolean isSunk() {
        return coordinates != null && !coordinates.isEmpty() && hitCoordinates.size() >= coordinates.size();
    }
}
