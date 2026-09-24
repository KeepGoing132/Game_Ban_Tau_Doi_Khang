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

    /**
     * Thẩm định xem bố trí tàu có hợp lệ theo chuẩn bài tập lớn không:
     * - Đúng 5 tàu
     * - Kích thước bộ tàu đúng: 1 tàu 5 ô, 1 tàu 4 ô, 2 tàu 3 ô, 1 tàu 2 ô (tổng 17 ô)
     * - Tọa độ 0..9
     * - Tàu thẳng hàng (ngang hoặc dọc)
     * - Không chồng lấn
     */
    public boolean validateAllShips() {
        if (ships == null || ships.size() != 5) {
            return false;
        }

        List<Integer> expectedSizes = new ArrayList<>(Arrays.asList(5, 4, 3, 3, 2));
        List<Integer> actualSizes = new ArrayList<>();
        Set<Coordinate> occupiedCells = new HashSet<>();

        for (Ship ship : ships) {
            if (ship.getCoordinates() == null || ship.getCoordinates().size() != ship.getSize()) {
                return false;
            }
            actualSizes.add(ship.getSize());

            // Kiểm tra tọa độ và tính thẳng hàng
            List<Coordinate> coords = ship.getCoordinates();
            if (!isValidShipGeometry(coords, ship.getSize())) {
                return false;
            }

            // Kiểm tra chồng lấn và biên
            for (Coordinate c : coords) {
                if (c.getX() < 0 || c.getX() >= SIZE || c.getY() < 0 || c.getY() >= SIZE) {
                    return false;
                }
                if (!occupiedCells.add(c)) {
                    // Trùng tọa độ ô khác
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

    /**
     * Nhận 1 phát bắn vào tọa độ c.
     * Trả về kết quả: MISS, HIT, hoặc SUNK.
     */
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

    /**
     * Kiểm tra xem toàn bộ 5 tàu đã bị bắn chìm chưa
     */
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
