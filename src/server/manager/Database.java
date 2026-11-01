package server.manager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MySQL settings and schema naming for the whole server.
 *
 * <p>Settings are read in this order: system properties ({@code -Dbattleship.db.host=...}, …), then
 * a {@code db.properties} file (path given by {@code -Dbattleship.db.properties}, otherwise the data
 * directory, otherwise the working directory), then the built-in development defaults
 * ({@code 127.0.0.1:3306}, user {@code root}). Keeping credentials in a file means the same JAR runs
 * against any server without a rebuild, and no password lives in the source code.</p>
 *
 * <p>Each data directory owns one schema, derived from its name, so a fresh directory starts from an
 * empty database while restarting the same directory finds its data again.</p>
 */
public final class Database {
    public static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 3306;
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";
    private static final String PROPERTIES_FILE = "db.properties";
    private static final int MAX_SCHEMA_LENGTH = 64;
    private static final int HASH_SUFFIX_LENGTH = 9;
    /** Schemas already created by this JVM; avoids a round trip on every connection. */
    private static final Set<String> READY = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private Database() {
    }

    // ------------------------------------------------------------------ settings

    private static volatile Map<String, String> cached;

    /** Re-reads the settings file; used by the tests after they change the environment. */
    public static void reload() {
        cached = null;
    }

    private static Map<String, String> settings() {
        Map<String, String> current = cached;
        if (current != null) {
            return current;
        }
        Map<String, String> values = new LinkedHashMap<>();
        Path file = propertiesFile();
        if (file != null) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("Không đọc được " + file + ": " + e.getMessage(), e);
            }
            for (String name : properties.stringPropertyNames()) {
                values.put(name.trim(), properties.getProperty(name).trim());
            }
        }
        cached = values;
        return values;
    }

    /** The settings file to read, or null when the installation relies on the defaults. */
    private static Path propertiesFile() {
        String explicit = System.getProperty("battleship.db.properties");
        if (explicit != null && !explicit.trim().isEmpty()) {
            return Paths.get(explicit.trim());
        }
        Path fromWorkingDirectory = Paths.get(PROPERTIES_FILE);
        if (Files.isRegularFile(fromWorkingDirectory)) {
            return fromWorkingDirectory;
        }
        Path dataDirectory = Paths.get(System.getProperty("battleship.data_dir", "data")).resolve(PROPERTIES_FILE);
        if (Files.isRegularFile(dataDirectory)) {
            return dataDirectory;
        }
        // last resort: next to the running code, i.e. beside battleship-server.jar or bin/
        Path nearby = codeDirectory();
        return nearby != null && Files.isRegularFile(nearby.resolve(PROPERTIES_FILE)) ? nearby.resolve(PROPERTIES_FILE) : null;
    }

    /** Directory holding the running classes or JAR, so a packaged server finds db.properties. */
    private static Path codeDirectory() {
        try {
            java.security.CodeSource source = Database.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path location = Paths.get(source.getLocation().toURI());
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The settings actually in use, as a properties file body. Lets a launcher (the packaging test)
     * hand the same connection to another process without repeating secrets on the command line.
     */
    public static String effectiveProperties() {
        StringBuilder text = new StringBuilder();
        text.append("host=").append(host()).append('\n');
        text.append("port=").append(port()).append('\n');
        text.append("user=").append(user()).append('\n');
        text.append("password=").append(password()).append('\n');
        String name = setting("name", null);
        if (name != null) {
            text.append("name=").append(name).append('\n');
        }
        String url = setting("url", null);
        if (url != null) {
            text.append("url=").append(url).append('\n');
        }
        return text.toString();
    }

    private static String setting(String name, String fallback) {
        String system = System.getProperty("battleship.db." + name);
        if (system != null && !system.trim().isEmpty()) {
            return system.trim();
        }
        String fromFile = settings().get(name);
        return fromFile == null || fromFile.isEmpty() ? fallback : fromFile;
    }

    public static String host() {
        return setting("host", DEFAULT_HOST);
    }

    public static int port() {
        String value = setting("port", String.valueOf(DEFAULT_PORT));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Cổng MySQL không hợp lệ trong cấu hình: " + value);
        }
    }

    public static String user() {
        return setting("user", DEFAULT_USER);
    }

    private static String password() {
        return setting("password", DEFAULT_PASSWORD);
    }

    /** Full JDBC URL, either taken from the configuration or built from host/port/schema. */
    public static String url(Path directory) {
        String explicit = setting("url", null);
        if (explicit != null) {
            return explicit;
        }
        return "jdbc:mysql://" + host() + ":" + port() + "/" + schemaName(directory) + options();
    }

    private static String options() {
        // useAffectedRows=true: a duplicate-key write reports 0 changed rows, which is how the chat
        // insert tells a new message from a retry (the driver otherwise reports "rows found")
        return "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                + "&characterEncoding=UTF-8&useUnicode=true&rewriteBatchedStatements=true&useAffectedRows=true";
    }

    /**
     * Schema for one data directory: {@code data} and {@code battleship} map to {@code battleship},
     * any other directory gets {@code battleship_<name>}. Long names are shortened and finished with
     * a path digest, so two different directories never share a schema.
     */
    public static String schemaName(Path directory) {
        String override = setting("name", null);
        if (override != null) {
            return sanitize(override);
        }
        Path absolute = directory.toAbsolutePath();
        Path last = absolute.getFileName();
        String clean = sanitize(last == null ? "data" : last.toString());
        String name = "data".equals(clean) || "battleship".equals(clean) ? "battleship" : "battleship_" + clean;
        if (name.length() > MAX_SCHEMA_LENGTH) {
            name = name.substring(0, MAX_SCHEMA_LENGTH - HASH_SUFFIX_LENGTH - 1) + "_" + digest(absolute.toString());
        }
        return name;
    }

    private static String sanitize(String value) {
        String clean = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return clean.isEmpty() ? "battleship" : clean;
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (int i = 0; text.length() < HASH_SUFFIX_LENGTH - 1; i++) {
                text.append(String.format(Locale.ROOT, "%02x", hash[i]));
            }
            return text.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không tính được tên schema", e);
        }
    }

    // ------------------------------------------------------------------ connections

    /** Opens a connection to the schema of this directory, creating the schema when it is missing. */
    public static Connection open(Path directory) throws SQLException {
        loadDriver();
        ensureSchema(directory);
        return DriverManager.getConnection(url(directory), user(), password());
    }

    /** Opens a connection without selecting a schema; used to create or drop one. */
    public static Connection openServer() throws SQLException {
        loadDriver();
        return DriverManager.getConnection("jdbc:mysql://" + host() + ":" + port() + "/" + options(), user(), password());
    }

    /** Creates the schema of this directory when it does not exist yet. */
    public static synchronized void ensureSchema(Path directory) throws SQLException {
        String schema = schemaName(directory);
        if (READY.contains(schema)) {
            return;
        }
        try (Connection server = openServer(); Statement statement = server.createStatement()) {
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        READY.add(schema);
    }

    /** Drops one schema; only used by the tests, which run against disposable databases. */
    public static synchronized void dropSchema(Path directory) throws SQLException {
        String schema = schemaName(directory);
        try (Connection server = openServer(); Statement statement = server.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS `" + schema + "`");
        }
        READY.remove(schema);
    }

    /** The schema this directory maps to, for messages and tests. */
    public static String describe(Path directory) {
        return "MySQL " + host() + ":" + port() + " / schema `" + schemaName(directory) + "` (user " + user() + ")";
    }

    private static void loadDriver() {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Thiếu driver MySQL (" + DRIVER + "). Hãy để mysql-connector-j trong lib/ hoặc trong classpath.", e);
        }
    }
}
