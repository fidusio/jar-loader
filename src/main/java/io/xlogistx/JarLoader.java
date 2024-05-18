package io.xlogistx;

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
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
            //jarsURLs(libDir);
            // Extract and load JAR files
            loadJars(libDir);

            // Execute the main method of the specified class
            executeMainClass(mainClass, args);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }




    private static Path extractLibDirectory(String libDir) throws IOException {

        File jarDir = new File(libDir);
        if(jarDir.isDirectory())
            return jarDir.toPath();


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
                            System.err.println("Failed to extract " + e.getName() +" " + ex);
                        }
                    });
        }

        return tempDir.toPath();
    }

    private static Path extractLibDirectoryMemFS(String libDir) throws IOException {

        File jarDir = new File(libDir);
        if(jarDir.isDirectory())
            return jarDir.toPath();

        FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
        Path path = fs.getPath("/temp");
        Files.createDirectories(path);



        //try (JarFile jarFile = new JarFile(new File(JarLoader.class.getProtectionDomain().getCodeSource().getLocation().getPath()))) {
        try (JarFile jarFile = new JarFile(new File(libDir))) {
            jarFile.stream()
                    .filter(e -> /*e.getName().startsWith(libDir + "/") &&*/ e.getName().endsWith(".jar"))
                    .forEach(e -> {
                        try
                        {
                            Path memFile = fs.getPath("/temp/" + e.getName());
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



    private static List<URL> listMatches(Path rootDir, String filterPattern, String filterExclusion)
            throws IOException
    {

        List<URL> ret = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(rootDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().matches(filterPattern) && !path.toString().matches(filterExclusion) && !path.toString().contains("jar-loader"))
                    .forEach(p ->
                            {
                                try
                                {
                                    ret.add(p.toUri().toURL());
                                }
                                catch (MalformedURLException e)
                                {
                                    e.printStackTrace();
                                }
                            });
        }

        return ret;
    }

    private static void loadJars(String libDirName)
            throws IOException
    {
        List<URL> jarURLs = listMatches(extractLibDirectoryMemFS(libDirName), JAR_PATTERN, JAR_EXCLUDE);

        if(!jarURLs.isEmpty())
        {
            URLClassLoader urlClassLoader = new URLClassLoader(jarURLs.toArray(new URL[0]), JarLoader.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(urlClassLoader);
            //System.out.println("Jars found:\n" + jarURLs);
        }
    }

    private static void executeMainClass(String mainClassName, String[] args)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> mainClass = classLoader.loadClass(mainClassName);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        String[] mainArgs = new String[args.length - 2];
        System.arraycopy(args, 2, mainArgs, 0, mainArgs.length);
        mainMethod.invoke(null, (Object) mainArgs);
    }
}
