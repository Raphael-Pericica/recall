import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class Backup {

    //where the files we want to protect are
    static final Path SOURCE_FOLDER = Path.of("test_data");

    //contents of backed up files are kept as a hash
    static final Path OBJECTS_FOLDER = Path.of("backup_store", "objects");

    // NEW (Phase 2): each backup run writes one snapshot file here,
    // listing every file name and the hash of its contents at that moment
    static final Path SNAPSHOTS_FOLDER = Path.of("backup_store", "snapshots");

    //same method as demo
    static String hashFile(Path file) throws Exception {
        byte[] contents = Files.readAllBytes(file);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = sha256.digest(contents);
        return HexFormat.of().formatHex(hashBytes);
    }

    // NEW (Phase 3): writes a compressed copy of a file into the store
    static void storeCompressed(Path source, Path destination) throws Exception {
        byte[] contents = Files.readAllBytes(source);
        try (GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(destination.toFile()))) {
            gzip.write(contents);
        }
    }

    public static void main(String[] args) throws Exception {
        //check folder exists
        Files.createDirectories(OBJECTS_FOLDER);
        Files.createDirectories(SNAPSHOTS_FOLDER);

        File[] files = SOURCE_FOLDER.toFile().listFiles();
        if (files == null) {
            System.out.println("Could not find the folder: " + SOURCE_FOLDER);
            return;
        }
        Arrays.sort(files);

        int storedCount = 0;
        int skippedCount = 0;

        // NEW (Phase 2): one line per file, e.g. "a.txt<TAB>b94d27..."
        List<String> snapshotLines = new ArrayList<>();

        for (File file : files) {
            if (!file.isFile()) {
                continue;  //skip sub folders
            }

            String fileHash = hashFile(file.toPath());
            Path storedCopy = OBJECTS_FOLDER.resolve(fileHash);

            // NEW (Phase 2): record this file in the snapshot, stored or skipped
            snapshotLines.add(file.getName() + "\t" + fileHash);

            if (Files.exists(storedCopy)) {
                System.out.println("SKIP   " + file.getName() + "  (already stored as " + fileHash.substring(0, 12) + ")");
                skippedCount++;
            } else {
                storeCompressed(file.toPath(), storedCopy);
                storedCopy.toFile().setReadOnly();   // NEW (Phase 3): lock the object
                long originalSize = file.length();
                long storedSize = Files.size(storedCopy);
                System.out.println("STORE  " + file.getName() + "  -> objects/" + fileHash.substring(0, 12)
                        + "  (" + originalSize + " -> " + storedSize + " bytes)");
                storedCount++;
            }
        }

        // NEW (Phase 2): save the snapshot, named by date and time
        // (dashes instead of colons, because Windows doesn't allow ':' in file names)
        String snapshotName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path snapshotFile = SNAPSHOTS_FOLDER.resolve(snapshotName + ".txt");
        Files.write(snapshotFile, snapshotLines);
        snapshotFile.toFile().setReadOnly();   // NEW (Phase 3): lock the snapshot too

        System.out.println();
        System.out.println("Backup finished: " + storedCount + " stored, " + skippedCount + " skipped.");
        System.out.println("Snapshot saved: " + snapshotName);
    }
}
