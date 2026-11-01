package server.manager;

import common.protocol.JsonUtil;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.Stream;

/** One synced, atomically published record per finished match, before MySQL is updated. */
final class ResultJournal {
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final long MAX_RECORD_BYTES = 1024 * 1024;
    private final Path directory;

    ResultJournal(Path directory) throws IOException { this.directory=directory; Files.createDirectories(directory); }

    private static String digest(byte[] bytes) {
        try {
            StringBuilder value=new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) value.append(String.format(Locale.ROOT,"%02x",b & 255));
            return value.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Path file(String id) { return directory.resolve(digest(id.getBytes(StandardCharsets.UTF_8))+".json"); }

    void stage(String id, Map<String,Object> payload) throws IOException {
        Path target=file(id);
        if (Files.exists(target)) {
            Map<String,Object> existing=read(target);
            if (!id.equals(existing.get("matchId"))) throw new IOException("Journal match id mismatch");
            return;
        }
        String json=JsonUtil.toJson(payload);
        Map<String,Object> envelope=new LinkedHashMap<>();
        envelope.put("version",1); envelope.put("payload",json);
        envelope.put("sha256",digest(json.getBytes(StandardCharsets.UTF_8)));
        byte[] bytes=JsonUtil.toJson(envelope).getBytes(StandardCharsets.UTF_8);
        if (bytes.length>MAX_RECORD_BYTES || totalBytes()+bytes.length>MAX_BYTES) throw new IOException("Result journal capacity exceeded");
        Path temporary=Files.createTempFile(directory,"result-",".tmp");
        try {
            try (FileOutputStream out=new FileOutputStream(temporary.toFile())) { out.write(bytes); out.getFD().sync(); }
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }

    List<Map<String,Object>> pending() throws IOException {
        List<Map<String,Object>> records=new ArrayList<>();
        try (Stream<Path> paths=Files.list(directory)) {
            Iterator<Path> iterator=paths.sorted().iterator();
            while (iterator.hasNext()) {
                Path path=iterator.next();
                if (path.getFileName().toString().matches("[a-f0-9]{64}\\.json")) records.add(read(path));
                else System.err.println("[Journal] Unpublished/unrecognized file retained: " + path.getFileName());
            }
        }
        // Original finish order makes reapplication deterministic across multiple results.
        records.sort(Comparator.comparingLong(m -> JsonUtil.getLong(m,"endedAt",0)));
        return records;
    }

    private Map<String,Object> read(Path path) throws IOException {
        try {
            if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>MAX_RECORD_BYTES)
                throw new IllegalArgumentException("Invalid journal file");
            Map<String,Object> envelope=JsonUtil.parseObject(new String(Files.readAllBytes(path),StandardCharsets.UTF_8));
            if (JsonUtil.requireInt(envelope,"version")!=1) throw new IllegalArgumentException("Unknown journal version");
            String json=JsonUtil.getString(envelope,"payload","");
            if (!digest(json.getBytes(StandardCharsets.UTF_8)).equals(envelope.get("sha256"))) throw new IllegalArgumentException("Journal checksum mismatch");
            Map<String,Object> payload=JsonUtil.parseObject(json);
            String id=JsonUtil.getString(payload,"matchId","");
            if (id.isEmpty() || !file(id).equals(path)) throw new IllegalArgumentException("Invalid journal match id");
            return payload;
        } catch (RuntimeException e) { throw new IOException("Corrupt result journal retained: " + path.getFileName(),e); }
    }

    private long totalBytes() throws IOException {
        long total=0;
        try (Stream<Path> paths=Files.list(directory)) {
            for (Iterator<Path> it=paths.iterator();it.hasNext();) total+=Files.size(it.next());
        }
        return total;
    }
    void complete(String id) throws IOException { Files.deleteIfExists(file(id)); }
}
