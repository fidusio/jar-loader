package io.xlogistx.jl;


import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
/**
 * Utility helper class for the jar loader
 */
public class JarUtil {

    public static final String JAR_DIR = "/tempLib";

    public static class ExecConfig {
        public final Path tempDir;
        public final String mainClass;
        public final FileSystem fileSystem;
        public final URLClassLoader mainClassLoader;

        private ExecConfig(Path tempDir, String mainClass, FileSystem fs, URLClassLoader classLoader) {
            this.tempDir = tempDir;
            this.mainClass = mainClass;
            this.fileSystem = fs;
            this.mainClassLoader = classLoader;
        }

    }


    private static FileSystem JIMFS = null;


    public static final String JAR_PATTERN = ".*\\.jar$";
    public static final String JAR_EXCLUDE = ".*-(javadoc|test|sources)\\.jar$";
    private static FileSystem currentFileSystem = null;

    public JarUtil() {
    }

    public static String findMainClass(String jarFilePath) throws IOException {
        try (JarFile jarFile = new JarFile(jarFilePath)) {
            return findMainClass(jarFile);
        }
    }


    public static String findMainClass(JarFile jarFile) throws IOException {
        Manifest manifest = jarFile.getManifest();
        Attributes mainAttributes = manifest.getMainAttributes();
        return mainAttributes.getValue("Main-Class");
    }


    public static FileSystem getJIMFS() {
        return JIMFS;
    }

    public static Path extractLibDirectory(String libDir) throws IOException {

        File jarDir = new File(libDir);
        if (jarDir.isDirectory())
            return jarDir.toPath();
        currentFileSystem = FileSystems.getDefault();


        File tempDir = Files.createTempDirectory("temp").toFile();
        tempDir.deleteOnExit();

        //try (JarFile jarFile = new JarFile(new File(JarLoader.class.getProtectionDomain().getCodeSource().getLocation().getPath()))) {
        try (JarFile jarFile = new JarFile(new File(libDir))) {
            jarFile.stream()
                    .filter(e -> /*e.getName().startsWith(libDir + "/") &&*/ e.getName().endsWith(".jar"))
                    .forEach(e -> {
                        try {
                            File outFile = new File(tempDir, e.getName());
                            outFile.getParentFile().mkdirs();
                            try (InputStream in = jarFile.getInputStream(e)) {
                                Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException ex) {
                            System.err.println("Failed to extract " + e.getName() + " " + ex);
                        }
                    });
        }

        return tempDir.toPath();
    }

    public static Path extractLibDirectoryMemFS(String libDir) throws IOException {

        File jarDir = new File(libDir);
        if (jarDir.isDirectory())
            return jarDir.toPath();

        FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
        currentFileSystem = fs;
        JIMFS = fs;
        Path path = fs.getPath(JAR_DIR);
        Files.createDirectories(path);


        //try (JarFile jarFile = new JarFile(new File(JarLoader.class.getProtectionDomain().getCodeSource().getLocation().getPath()))) {
        try (JarFile jarFile = new JarFile(new File(libDir))) {
            jarFile.stream()
                    .filter(e -> /*e.getName().startsWith(libDir + "/") &&*/ e.getName().endsWith(".jar"))
                    .forEach(e -> {
                        try {
                            String pathName = JAR_DIR + "/" + e.getName();
                            Path memFile = fs.getPath(pathName);
                            Files.createDirectories(memFile.getParent());
                            Files.createFile(memFile);


                            try (InputStream in = jarFile.getInputStream(e)) {
                                Files.copy(in, memFile, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException ex) {
                            ex.printStackTrace();
                            //throw new RuntimeException("Failed to extract " + e.getName(), ex);
                        }
                    });
        }


//        try (JarFile jarFile = new JarFile(new File(libDir))) {
//            Path dest = fs.getPath("/html");
//            Files.createDirectories(dest.getFileName());
//            copyHtmlFromJar(jarFile, "html/", dest);
//        }

        return path;
    }

    public static void zipISToOutputPath(ZipInputStream zis, Path outputPath) throws IOException {
        try {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = outputPath.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (OutputStream out = Files.newOutputStream(entryPath)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        } finally {
            zis.close();

        }
    }

    public static ZipInputStream convertISToZip(InputStream is)
            throws IOException {
        try {
            if (is instanceof ByteArrayInputStream)
                return new ZipInputStream(is);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            return new ZipInputStream(bais);
        } finally {
            if (!(is instanceof ByteArrayInputStream) && is != null)
                is.close();
        }
    }

    public static JarInputStream convertISToJar(InputStream is)
            throws IOException {
        try {
            if (is instanceof ByteArrayInputStream)
                return new JarInputStream(is);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            return new JarInputStream(bais);
        } finally {
            if (!(is instanceof ByteArrayInputStream) && is != null)
                is.close();
        }
    }


    public static List<URL> listMatches(Path rootDir, String filterPattern, String filterExclusion)
            throws IOException {

        List<URL> ret = new ArrayList<>();
        Predicate<Path> composition;
        Predicate<Path> pattern = p -> p.toString().matches(filterPattern);
        if (filterExclusion != null && !filterExclusion.trim().isEmpty()) {
            Predicate<Path> exclusion = p -> p.toString().matches(filterExclusion);
            composition = pattern.and(exclusion.negate());
        } else {
            composition = pattern;
        }

        try (Stream<Path> paths = Files.walk(rootDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(composition)
                    .forEach(p ->
                    {
                        try {
                            ret.add(p.toUri().toURL());
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    });
        }

        return ret;
    }


    public static void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteDirectoryRecursively(entry);
                }
            }
        }

        Files.delete(path);
    }

    public static ExecConfig loadJars(boolean mem, String fatJarName, boolean extractMainClass)
            throws IOException {

        Path fatJarPath = mem ? extractLibDirectoryMemFS(fatJarName) : extractLibDirectory(fatJarName);
        List<URL> jarURLs = listMatches(fatJarPath, JAR_PATTERN, JAR_EXCLUDE);
        jarURLs.add(new File(fatJarName).toURI().toURL());
        String mainClass = null;
        if (extractMainClass) {
            mainClass = JarUtil.findMainClass(fatJarName);
        }

        URLClassLoader urlClassLoader = null;
        if (!jarURLs.isEmpty()) {
            urlClassLoader = new URLClassLoader(jarURLs.toArray(new URL[0]), JarLoader.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(urlClassLoader);
        }

        return new ExecConfig(fatJarPath, mainClass, currentFileSystem, urlClassLoader);
    }

    public static void executeMainClass(String mainClassName, List<String> args)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> mainClass = classLoader.loadClass(mainClassName);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        String[] mainArgs = args.toArray(new String[0]);
        mainMethod.invoke(null, (Object) mainArgs);
    }


    public static void printFileSystem(FileSystem fs) throws IOException {
        for (Path root : fs.getRootDirectories()) {
            Files.walk(root)
                    .forEach(path -> {
                        Path relPath = root.relativize(path);
                        String display = relPath.toString().isEmpty() ? root.toString() : relPath.toString();
                        JarLoader.print(display);
                    });
        }
    }

    public static String toStringFileSystem(FileSystem fs) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path root : fs.getRootDirectories()) {
            Files.walk(root)
                    .forEach(path -> {
                        Path relPath = root.relativize(path);
                        sb.append(relPath.toString().isEmpty() ? root.toString() : relPath.toString());
                        sb.append("\n");

                    });
        }
        return sb.toString();
    }

    public static void copyHtmlFromJar(JarFile fromJar, String jarResourceDir, Path targetDir) throws IOException {
        if (!jarResourceDir.endsWith("/")) {
            jarResourceDir += "/";
        }

        Enumeration<JarEntry> entries = fromJar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entryName.startsWith(jarResourceDir)) {
                String relativePath = entryName.substring(jarResourceDir.length());
                if (relativePath.isEmpty()) continue; // Skip the directory itself

                Path outPath = targetDir.resolve(relativePath);

                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    try (InputStream is = fromJar.getInputStream(entry)) {
                        Files.copy(is, outPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }


    public static void copyJarContent(JarFile jarFile, Path targetDir) throws IOException {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();
            Path outPath = targetDir.resolve(entryName);

            if (entry.isDirectory()) {
                Files.createDirectories(outPath);
            } else {
                Files.createDirectories(outPath.getParent());
                try (InputStream is = jarFile.getInputStream(entry)) {
                    Files.copy(is, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

}
