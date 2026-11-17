import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class Detect {

    //same store folders as backup and restore
    static final Path OBJECTS_FOLDER = Path.of("backup_store", "objects");
    static final Path SNAPSHOTS_FOLDER = Path.of("backup_store", "snapshots");

    //if this much of the folder changes in one backup thats suspicious (0.5 = half)
    static final double CHANGE_LIMIT = 0.5;

    //entropy goes from 0 to 8, normal text is about 4-5, encrypted data is nearly 8
    static final double ENTROPY_LIMIT = 7.5;

    //files smaller than this are too small to measure entropy properly
    static final int MIN_SIZE_FOR_ENTROPY = 1024;

    //reads a snapshot file into a map of file name -> hash
    static HashMap<String, String> readSnapshot(File snapshotFile) throws Exception {
        HashMap<String, String> files = new HashMap<>();
        List<String> lines = Files.readAllLines(snapshotFile.toPath());
        for (String line : lines) {
            String[] parts = line.split("\t");
            files.put(parts[0], parts[1]);
        }
        return files;
    }

    //gets the original bytes back out of a compressed object
    static byte[] readObject(String hash) throws Exception {
        Path storedCopy = OBJECTS_FOLDER.resolve(hash);
        try (GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(storedCopy.toFile()))) {
            return gzip.readAllBytes();
        }
    }

    //shannon entropy: how random the bytes look, in bits per byte (0 to 8)
    static double entropy(byte[] data) {
        //count how many times each of the 256 possible byte values shows up
        int[] counts = new int[256];
        for (byte b : data) {
            counts[b & 0xFF]++;  //& 0xFF turns java's -128..127 into 0..255
        }

        double result = 0;
        for (int count : counts) {
            if (count == 0) {
                continue;
            }
            double p = (double) count / data.length;
            result -= p * (Math.log(p) / Math.log(2));  //log base 2
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        File[] snapshotFiles = SNAPSHOTS_FOLDER.toFile().listFiles();
        if (snapshotFiles == null || snapshotFiles.length < 2) {
            System.out.println("Need at least 2 snapshots to compare. Run Backup.java twice.");
            return;
        }
        Arrays.sort(snapshotFiles);

        //compare the newest snapshot with the one before it
        File previousFile = snapshotFiles[snapshotFiles.length - 2];
        File latestFile = snapshotFiles[snapshotFiles.length - 1];
        HashMap<String, String> previous = readSnapshot(previousFile);
        HashMap<String, String> latest = readSnapshot(latestFile);

        System.out.println("Comparing " + previousFile.getName().replace(".txt", "")
                + "  ->  " + latestFile.getName().replace(".txt", ""));
        System.out.println();

        int changedCount = 0;
        int deletedCount = 0;
        int encryptedLookingCount = 0;

        //check every file in the newest snapshot
        for (String fileName : latest.keySet()) {
            String newHash = latest.get(fileName);
            String oldHash = previous.get(fileName);

            if (oldHash != null && oldHash.equals(newHash)) {
                continue;  //unchanged so nothing to check
            }

            String status = (oldHash == null) ? "NEW    " : "CHANGED";
            if (oldHash != null) {
                changedCount++;
            }

            //measure entropy of the new version of the file
            byte[] contents = readObject(newHash);
            String entropyNote = "";
            if (contents.length >= MIN_SIZE_FOR_ENTROPY) {
                double e = entropy(contents);
                entropyNote = String.format("  entropy %.2f", e);
                if (e > ENTROPY_LIMIT) {
                    entropyNote += "  <- looks encrypted";
                    encryptedLookingCount++;
                }
            }
            System.out.println(status + "  " + fileName + entropyNote);
        }

        //files that were there before but are gone now
        for (String fileName : previous.keySet()) {
            if (!latest.containsKey(fileName)) {
                System.out.println("DELETED  " + fileName);
                deletedCount++;
            }
        }

        double changeRate = (double) (changedCount + deletedCount) / previous.size();

        System.out.println();
        System.out.printf("%d of %d files changed or deleted (%.0f%%)%n",
                changedCount + deletedCount, previous.size(), changeRate * 100);
        System.out.println(encryptedLookingCount + " files look encrypted");
        System.out.println();

        //the verdict
        boolean tooManyChanges = changeRate >= CHANGE_LIMIT;
        boolean looksEncrypted = encryptedLookingCount > 0;

        if (tooManyChanges && looksEncrypted) {
            System.out.println("ALERT: possible ransomware attack");
            System.out.println("Do not trust the newest snapshot. Restore from the one before it:");
            System.out.println("   java Restore.java " + previousFile.getName().replace(".txt", ""));
        } else if (tooManyChanges || looksEncrypted) {
            System.out.println("WARNING: unusual changes, worth checking");
        } else {
            System.out.println("OK: nothing suspicious");
        }
    }
}
