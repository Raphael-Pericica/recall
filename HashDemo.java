import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;

public class HashDemo {

    
    static String hashFile(File file) throws Exception {
        byte[] contents = Files.readAllBytes(file.toPath());
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = sha256.digest(contents);
        return HexFormat.of().formatHex(hashBytes);
    }

    public static void main(String[] args) throws Exception {
        File folder = new File("test_data");
        File[] files = folder.listFiles();

        if (files == null) {
            System.out.println("Could not find the test_data folder.");
            return;
        }
        Arrays.sort(files);

        HashMap<String, String> seenHashes = new HashMap<>();

        for (File file : files) {
            String fileHash = hashFile(file);
            System.out.println(file.getName() + " -> " + fileHash.substring(0, 12));

            if (seenHashes.containsKey(fileHash)) {
                System.out.println("   duplicate of " + seenHashes.get(fileHash) + " - would NOT store again");
            } else {
                seenHashes.put(fileHash, file.getName());
            }
        }

        System.out.println("Files found: " + files.length + " | Unique files to store: " + seenHashes.size());
    }
}

