import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class Restore {

    // Same store folders that Backup.java writes to
    static final Path OBJECTS_FOLDER = Path.of("backup_store", "objects");
    static final Path SNAPSHOTS_FOLDER = Path.of("backup_store", "snapshots");

    // Restored files go into a NEW folder, so we never overwrite the originals
    static final Path RESTORE_FOLDER = Path.of("restored");

    // Same hashing method again. Three copies now (HashDemo, Backup, Restore):
    // we'll move it into one shared class in a later phase.
    static String hashFile(Path file) throws Exception {
        byte[] contents = Files.readAllBytes(file);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = sha256.digest(contents);
        return HexFormat.of().formatHex(hashBytes);
    }

    // NEW (Phase 3): reads a compressed object and writes the original file back out
    static void restoreDecompressed(Path storedCopy, Path destination) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(storedCopy.toFile()))) {
            byte[] contents = gzip.readAllBytes();
            Files.write(destination, contents);
        }
    }

    // Prints every snapshot that exists, oldest first
    static void listSnapshots() {
        File[] snapshotFiles = SNAPSHOTS_FOLDER.toFile().listFiles();
        if (snapshotFiles == null || snapshotFiles.length == 0) {
            System.out.println("No snapshots found. Run Backup.java first.");
            return;
        }
        Arrays.sort(snapshotFiles);

        System.out.println("Available snapshots:");
        for (File snapshotFile : snapshotFiles) {
            String name = snapshotFile.getName().replace(".txt", "");
            System.out.println("   " + name);
        }
        System.out.println();
        System.out.println("To restore one, run:  java Restore.java <snapshot name>");
    }

    public static void main(String[] args) throws Exception {
        // No snapshot name given? Just show the list and stop.
        if (args.length == 0) {
            listSnapshots();
            return;
        }

        String snapshotName = args[0].replace(".txt", "");
        Path snapshotFile = SNAPSHOTS_FOLDER.resolve(snapshotName + ".txt");

        if (!Files.exists(snapshotFile)) {
            System.out.println("No snapshot called: " + snapshotName);
            System.out.println();
            listSnapshots();
            return;
        }

        // e.g. restored/2026-09-29_14-30-05/
        Path targetFolder = RESTORE_FOLDER.resolve(snapshotName);
        Files.createDirectories(targetFolder);

        // Each line looks like: "a.txt<TAB>b94d27..."
        List<String> snapshotLines = Files.readAllLines(snapshotFile);

        int restoredCount = 0;
        int errorCount = 0;

        for (String line : snapshotLines) {
            String[] parts = line.split("\t");
            String fileName = parts[0];
            String expectedHash = parts[1];

            Path storedCopy = OBJECTS_FOLDER.resolve(expectedHash);
            Path restoredFile = targetFolder.resolve(fileName);

            // The snapshot points at content that isn't in the store
            if (!Files.exists(storedCopy)) {
                System.out.println("MISSING    " + fileName + "  (no object " + expectedHash.substring(0, 12) + ")");
                errorCount++;
                continue;
            }

            // NEW (Phase 3): decompress; a badly damaged object is reported, not a crash
            try {
                restoreDecompressed(storedCopy, restoredFile);
            } catch (Exception e) {
                System.out.println("CORRUPTED  " + fileName + "  (could not decompress)");
                errorCount++;
                continue;
            }

            // Verify: hash the restored file and check it matches the snapshot.
            // This is the "0 errors" part of the 3-2-1-1-0 rule.
            String actualHash = hashFile(restoredFile);
            if (actualHash.equals(expectedHash)) {
                System.out.println("OK         " + fileName);
                restoredCount++;
            } else {
                System.out.println("CORRUPTED  " + fileName + "  (hash does not match!)");
                errorCount++;
            }
        }

        System.out.println();
        System.out.println("Restored " + restoredCount + " files into " + targetFolder + " with " + errorCount + " errors.");
    }
}
