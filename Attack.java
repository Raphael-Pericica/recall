import java.io.File;
import java.nio.file.Files;
import java.util.Random;

//FAKE ransomware for testing Detect.java
//it overwrites every file in test_data with random bytes so they look encrypted
//it ONLY touches test_data, and Restore.java can get the files back
public class Attack {

    public static void main(String[] args) throws Exception {
        File[] files = new File("test_data").listFiles();
        if (files == null) {
            System.out.println("Could not find the test_data folder.");
            return;
        }

        Random random = new Random();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            //real encryption output looks like random bytes, so random bytes are a good fake
            byte[] scrambled = new byte[4096];
            random.nextBytes(scrambled);
            Files.write(file.toPath(), scrambled);
            System.out.println("encrypted " + file.getName());
        }
        System.out.println();
        System.out.println("test_data has been 'encrypted'. Now run Backup.java then Detect.java");
    }
}
