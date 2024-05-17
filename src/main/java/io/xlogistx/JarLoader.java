package io.xlogistx;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class JarLoader {

    public static final String JAR_PATTERN = ".*\\.jar$";
    public static final String JAR_EXCLUDE = ".*-(javadoc|test|sources)\\.jar$";

    public static void main(String[] args){
        if (args.length < 2) {
            System.err.println("Usage: java JarLoader <lib-directory> <main-class> [parmeters....]");
            System.exit(1);
        }

        String libDir = args[0];
        String mainClass = args[1];

        try {
            // Extract and load JAR files
            File tempLibDir = extractLibDirectory(libDir);
            loadJars(tempLibDir);

            // Execute the main method of the specified class
            executeMainClass(mainClass, args);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static File extractLibDirectory(String libDir) throws IOException {
        File jarDir = new File(libDir);
        if(jarDir.isDirectory())
            return jarDir;


        File tempDir = Files.createTempDirectory("tempLib").toFile();
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
                            throw new RuntimeException("Failed to extract " + e.getName(), ex);
                        }
                    });
        }

        return tempDir;
    }

    private static List<File> listMatches(File libDir, String filterPattern, String filterExclusion) throws IOException {
        Path rootDir = libDir.toPath();
        List<File> ret = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(rootDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().matches(filterPattern) && !path.toString().matches(filterExclusion) && !path.toString().contains("jar-loader"))
                    .forEach(p -> ret.add(p.toFile()));
        }

        return ret;
    }

    private static void loadJars(File libDir) throws IOException {
        List<File> matches = listMatches(libDir, JAR_PATTERN, JAR_EXCLUDE);


        File[] jarFiles = matches.toArray(new File[0]);

        if (jarFiles != null) {
            URL[] urls = new URL[jarFiles.length];
            for (int i = 0; i < jarFiles.length; i++) {
                urls[i] = jarFiles[i].toURI().toURL();
            }
            URLClassLoader urlClassLoader = new URLClassLoader(urls, JarLoader.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(urlClassLoader);
            System.out.println("Jars found:\n" + Arrays.toString(jarFiles));
        }
    }

    private static void executeMainClass(String mainClassName, String[] args) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> mainClass = classLoader.loadClass(mainClassName);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        String[] mainArgs = new String[args.length - 2];
        System.arraycopy(args, 2, mainArgs, 0, mainArgs.length);
        mainMethod.invoke(null, (Object) mainArgs);
    }
}
