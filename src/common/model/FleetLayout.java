package common.model;

import common.protocol.JsonUtil;
import java.util.*;

public final class FleetLayout {
    private FleetLayout() {}
    public static List<Map<String,Object>> encode(List<Ship> ships) {
        List<Map<String,Object>> fleet = new ArrayList<>();
        for (Ship ship : ships) {
            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("id", ship.getId()); entry.put("size", ship.getSize());
            List<Map<String,Object>> coordinates = new ArrayList<>();
            for (Coordinate c : ship.getCoordinates()) {
                Map<String,Object> point = new LinkedHashMap<>();
                point.put("x", c.getX()); point.put("y", c.getY()); coordinates.add(point);
            }
            entry.put("coordinates", coordinates); fleet.add(entry);
        }
        return fleet;
    }
    public static List<Ship> decode(Object value) {
        if (!(value instanceof List) || ((List<?>)value).size() != 5)
            throw new IllegalArgumentException("Mẫu phải có đủ 5 tàu");
        List<Ship> ships = new ArrayList<>();
        for (Object item : (List<?>)value) {
            if (!(item instanceof Map)) throw new IllegalArgumentException("Mẫu tàu không hợp lệ");
            @SuppressWarnings("unchecked") Map<String,Object> entry = (Map<String,Object>)item;
            String id = JsonUtil.getString(entry,"id","");
            int size = JsonUtil.requireInt(entry,"size");
            List<Object> raw = JsonUtil.getList(entry,"coordinates");
            if (id.trim().isEmpty() || id.length() > 64 || raw == null || raw.size() != size || size < 2 || size > 5)
                throw new IllegalArgumentException("Mẫu tàu không hợp lệ");
            List<Coordinate> cells = new ArrayList<>();
            for (Object point : raw) {
                if (!(point instanceof Map)) throw new IllegalArgumentException("Tọa độ không hợp lệ");
                @SuppressWarnings("unchecked") Map<String,Object> p = (Map<String,Object>)point;
                cells.add(new Coordinate(JsonUtil.requireInt(p,"x"), JsonUtil.requireInt(p,"y")));
            }
            ships.add(new Ship(id,size,cells));
        }
        Board board = new Board(); board.setShips(ships);
        if (!board.validateAllShips()) throw new IllegalArgumentException("Mẫu có tàu chồng lấn, vượt biên hoặc sai kích thước");
        return ships;
    }
}
