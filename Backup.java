import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

public class Backup {

    //where the files we want to protect are
    static final Path SOURCE_FOLDER = Path.of("test_data");

    //contents of backed up files are kept as a hash
    static final Path OBJECTS_FOLDER = Path.of("backup_store", "objects");

    //same method as demo
    static String hashFile(Path file) throws Exception {
        byte[] contents = Files.readAllBytes(file);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = sha256.digest(contents);
        return HexFormat.of().formatHex(hashBytes);
    }

    public static void main(String[] args) throws Exception {
        //check folder exists
        Files.createDirectories(OBJECTS_FOLDER);

        File[] files = SOURCE_FOLDER.toFile().listFiles();
        if (files == null) {
            System.out.println("Could not find the folder: " + SOURCE_FOLDER);
            return;
        }
        Arrays.sort(files);

        int storedCount = 0;
        int skippedCount = 0;

        for (File file : files) {
            if (!file.isFile()) {
                continue;  //skip sub folders
            }

            String fileHash = hashFile(file.toPath());
            Path storedCopy = OBJECTS_FOLDER.resolve(fileHash);

            if (Files.exists(storedCopy)) {
                System.out.println("SKIP   " + file.getName() + "  (already stored as " + fileHash.substring(0, 12) + ")");
                skippedCount++;
            } else {
                Files.copy(file.toPath(), storedCopy);
                System.out.println("STORE  " + file.getName() + "  -> objects/" + fileHash.substring(0, 12));
                storedCount++;
            }
        }

        System.out.println();
        System.out.println("Backup finished: " + storedCount + " stored, " + skippedCount + " skipped.");
    }
}
