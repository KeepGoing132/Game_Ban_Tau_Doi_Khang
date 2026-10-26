package client.model;

import common.model.FleetLayout;
import common.model.Ship;
import common.protocol.JsonUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.prefs.*;

/** Five local slots, isolated by endpoint and immutable account id. No credentials are stored. */
public final class FleetPresets {
    private final Preferences preferences;
    public FleetPresets(String endpoint, int accountId) {
        this(Preferences.userRoot().node("battleship/fleet-presets"), endpoint, accountId);
    }
    public FleetPresets(Preferences root, String endpoint, int accountId) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest((endpoint.toLowerCase(Locale.ROOT) + "/" + accountId).getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte b : bytes) key.append(String.format(Locale.ROOT,"%02x",b & 255));
            preferences = root.node(key.toString());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Map<Integer,String> names() {
        Map<Integer,String> names = new LinkedHashMap<>();
        for (int slot=0;slot<5;slot++) {
            String name = preferences.get("name"+slot,null);
            if (name != null) names.put(slot,name);
        }
        return names;
    }
    public void save(int slot, String name, List<Ship> ships) throws BackingStoreException {
        requireSlot(slot); name = requireName(name);
        List<Map<String,Object>> encoded = FleetLayout.encode(ships);
        FleetLayout.decode(encoded);
        preferences.put("fleet"+slot, JsonUtil.toJson(encoded));
        preferences.put("name"+slot, name); preferences.flush();
    }
    public List<Ship> load(int slot) {
        requireSlot(slot);
        return FleetLayout.decode(JsonUtil.parseList(preferences.get("fleet"+slot,"[]")));
    }
    public void rename(int slot,String name) throws BackingStoreException {
        requireSlot(slot);
        if (!names().containsKey(slot)) throw new IllegalArgumentException("Chưa có mẫu trong ô này");
        preferences.put("name"+slot,requireName(name)); preferences.flush();
    }
    public void delete(int slot) throws BackingStoreException {
        requireSlot(slot); preferences.remove("fleet"+slot); preferences.remove("name"+slot); preferences.flush();
    }
    private static void requireSlot(int slot) { if (slot<0 || slot>=5) throw new IllegalArgumentException("Chỉ có 5 ô lưu mẫu"); }
    private static String requireName(String name) {
        if (name==null || name.trim().isEmpty() || name.trim().length()>32)
            throw new IllegalArgumentException("Tên mẫu cần từ 1 đến 32 ký tự");
        return name.trim();
    }
}
