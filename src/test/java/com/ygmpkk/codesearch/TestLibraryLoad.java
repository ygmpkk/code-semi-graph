import java.nio.file.Files;
import java.nio.file.Path;

public class TestLibraryLoad {
    public static void main(String[] args) {
        String libraryPath = System.getProperty("java.library.path");
        System.out.println("java.library.path: " + libraryPath);
        System.out.println("os.name: " + System.getProperty("os.name"));
        System.out.println("os.arch: " + System.getProperty("os.arch"));

        String libraryName = "libjava-tree-sitter.dylib";
        String[] paths = libraryPath.split(System.getProperty("path.separator"));

        for (String path : paths) {
            Path libFile = Path.of(path, libraryName);
            System.out.println("Checking: " + libFile);
            System.out.println("  Exists: " + Files.exists(libFile));
            if (Files.exists(libFile)) {
                try {
                    System.out.println("  Attempting to load: " + libFile.toAbsolutePath());
                    System.load(libFile.toAbsolutePath().toString());
                    System.out.println("  SUCCESS!");
                } catch (UnsatisfiedLinkError e) {
                    System.out.println("  FAILED: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}

