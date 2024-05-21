import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.io.IOException;

public class CheckMainClass {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java CheckMainClass <path-to-jar>");
            return;
        }

        String jarFilePath = args[0];

        try (JarFile jarFile = new JarFile(jarFilePath)) {
            Manifest manifest = jarFile.getManifest();
            Attributes mainAttributes = manifest.getMainAttributes();
            String mainClass = mainAttributes.getValue("Main-Class");

            if (mainClass != null) {
                System.out.println("Main-Class found: " + mainClass);
            } else {
                System.out.println("No Main-Class attribute found in manifest.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
