package io.xlogistx.jl;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public class JarUtil {

    public static final String JAR_DIR = "/tempLib";
    public static class ExecConfig {
        public final Path tempDir;
        public final String mainClass;
        public final FileSystem fileSystem;

        private ExecConfig(Path tempDir, String mainClass, FileSystem fs) {
            this.tempDir = tempDir;
            this.mainClass = mainClass;
            this.fileSystem = fs;
        }
    }


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


    public static Path extractLibDirectory(String libDir) throws IOException {

        File jarDir = new File(libDir);
        if (jarDir.isDirectory())
            return jarDir.toPath();
        currentFileSystem = FileSystems.getDefault();


        File tempDir = Files.createTempDirectory(JAR_DIR).toFile();
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
        Path path = fs.getPath(JAR_DIR);
        Files.createDirectories(path);


        //try (JarFile jarFile = new JarFile(new File(JarLoader.class.getProtectionDomain().getCodeSource().getLocation().getPath()))) {
        try (JarFile jarFile = new JarFile(new File(libDir))) {
            jarFile.stream()
                    .filter(e -> /*e.getName().startsWith(libDir + "/") &&*/ e.getName().endsWith(".jar"))
                    .forEach(e -> {
                        try {
                            Path memFile = fs.getPath(JAR_DIR + "/" + e.getName());
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

        return path;
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

        if (!jarURLs.isEmpty()) {
            URLClassLoader urlClassLoader = new URLClassLoader(jarURLs.toArray(new URL[0]), JarLoader.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(urlClassLoader);
        }

        return new ExecConfig(fatJarPath, mainClass, currentFileSystem);
    }

    public static void executeMainClass(String mainClassName, List<String> args)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> mainClass = classLoader.loadClass(mainClassName);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        String[] mainArgs = args.toArray(new String[0]);
        mainMethod.invoke(null, (Object) mainArgs);
    }
}
