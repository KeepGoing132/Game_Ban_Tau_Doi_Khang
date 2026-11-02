package common.model;

import common.config.GameConfig;

import java.util.*;

public class Board {
    public static final int SIZE = GameConfig.BOARD_SIZE;
    private List<Ship> ships = new ArrayList<>();
    private Set<Coordinate> shotHistory = new HashSet<>();

    public Board() {
    }

    public List<Ship> getShips() {
        return ships;
    }

    public void setShips(List<Ship> ships) {
        this.ships = ships != null ? ships : new ArrayList<>();
    }

    public Set<Coordinate> getShotHistory() {
        return shotHistory;
    }

    public void setShotHistory(Set<Coordinate> shotHistory) {
        this.shotHistory = shotHistory != null ? shotHistory : new HashSet<>();
    }

    public boolean hasBeenShot(Coordinate c) {
        return shotHistory.contains(c);
    }

    public Ship getShipAt(Coordinate c) {
        for (Ship ship : ships) {
            if (ship.contains(c)) {
                return ship;
            }
        }
        return null;
    }

    public boolean validateAllShips() {
        if (ships == null || ships.size() != 5) {
            return false;
        }

        List<Integer> expectedSizes = new ArrayList<>(Arrays.asList(5, 4, 3, 3, 2));
        List<Integer> actualSizes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Coordinate> occupiedCells = new HashSet<>();

        for (Ship ship : ships) {
            if (ship == null || ship.getId() == null || ship.getId().trim().isEmpty() || !ids.add(ship.getId())) return false;
            if (ship.getCoordinates() == null || ship.getCoordinates().size() != ship.getSize()) {
                return false;
            }
            if (ship.getCoordinates().contains(null)) return false;
            actualSizes.add(ship.getSize());

            List<Coordinate> coords = ship.getCoordinates();
            if (!isValidShipGeometry(coords, ship.getSize())) {
                return false;
            }

            for (Coordinate c : coords) {
                if (c.getX() < 0 || c.getX() >= SIZE || c.getY() < 0 || c.getY() >= SIZE) {
                    return false;
                }
                if (!occupiedCells.add(c)) {
                    return false;
                }
            }
        }

        Collections.sort(expectedSizes);
        Collections.sort(actualSizes);
        if (!expectedSizes.equals(actualSizes)) {
            return false;
        }

        return occupiedCells.size() == GameConfig.TOTAL_SHIP_CELLS;
    }

    private boolean isValidShipGeometry(List<Coordinate> coords, int size) {
        if (coords.size() != size) return false;
        if (size == 1) return true;

        boolean horizontal = true;
        boolean vertical = true;
        int firstX = coords.get(0).getX();
        int firstY = coords.get(0).getY();

        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();

        for (Coordinate c : coords) {
            if (c.getY() != firstY) horizontal = false;
            if (c.getX() != firstX) vertical = false;
            xs.add(c.getX());
            ys.add(c.getY());
        }

        if (!horizontal && !vertical) return false;

        if (horizontal) {
            Collections.sort(xs);
            for (int i = 0; i < xs.size() - 1; i++) {
                if (xs.get(i + 1) - xs.get(i) != 1) return false;
            }
        } else {
            Collections.sort(ys);
            for (int i = 0; i < ys.size() - 1; i++) {
                if (ys.get(i + 1) - ys.get(i) != 1) return false;
            }
        }

        return true;
    }

    public ShotResult receiveShot(Coordinate c) {
        shotHistory.add(c);
        Ship hitShip = getShipAt(c);
        if (hitShip == null) {
            return ShotResult.MISS;
        }

        hitShip.recordHit(c);
        if (hitShip.isSunk()) {
            return ShotResult.SUNK;
        }
        return ShotResult.HIT;
    }

    public boolean areAllShipsSunk() {
        if (ships.isEmpty()) return false;
        for (Ship ship : ships) {
            if (!ship.isSunk()) {
                return false;
            }
        }
        return true;
    }

    public int getRemainingShipsCount() {
        int count = 0;
        for (Ship ship : ships) {
            if (!ship.isSunk()) {
                count++;
            }
        }
        return count;
    }
}
