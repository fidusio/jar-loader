package io.xlogistx.jl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JarLoader {

    public static void print(String str) {
        System.out.println("***** JarLoader ***** " + str);
    }

    public static void error(String str) {
        System.err.println("***** JarLoader ***** " + str);
    }

    public static final String APP_VERSION = "1.1.1";


    private static void execProgram(String[] args)
            throws Exception {
        if (args.length < 2) {
            System.err.println("*** jar-loader version " + APP_VERSION + " ***");
            System.err.println("Usage: java -jar jar-loader.jar [-f] [-jar] <app-fat.jar> [main-class] [parameters...]");
            System.err.println("[-f]: if specified expand the jar files inside app-fat.jar in a temp dir of the file system, " +
                    "if omitted will use jimfs(in memory).");
            System.err.println("[-jar]: if the app-fat.jar has a main-class.");
            System.err.println("[main-class]: required if [-jar] was omitted.");
            System.err.println("[parameters]: if required by the main-class.");

            System.exit(-1);
        }

        List<String> argsList = new ArrayList<>();
        Collections.addAll(argsList, args);

        boolean mem = !"-f".equals(argsList.get(0));

        if (!mem) {
            argsList.remove(0);
        }


        // if -jar is an argument => the fat jar supposed to have main class
        // that main class must be extracted
        boolean extractMainClass = "-jar".equals(argsList.get(0));

        if (extractMainClass)
            // remove -jar from the program parameters
            argsList.remove(0);


        // remove the fat jar from the program parameters
        // and set it as fatJarPath
        String fatJarPath = argsList.remove(0);

        // try to guess the main class name
        // if  -jar was set extract from fatJar ie: java -jar jar-loader.jar -jar fatJar.jar param1 param2...
        // if not it must be the next argument ie: java -jar jar-loader fatJar.jar org.acme.main param1 param2...
        String mainClass = extractMainClass ? null : argsList.remove(0);


        //jarsURLs(libDir);
        // Extract and load JAR files
        JarUtil.ExecConfig execConfig = JarUtil.loadJars(mem, fatJarPath, extractMainClass);

        // Execute the main method of the specified class
        if (extractMainClass)
            mainClass = execConfig.mainClass;

        print("Temp lib path: " + execConfig.tempDir.toUri().toURL());
        print("Running: " + mainClass + " " + argsList);


        try {
            // special case if zoxweb-core-[version-2.3.8+] used

            Class<?> clazz = execConfig.mainClassLoader.loadClass("org.zoxweb.shared.util.ResourceManager");
            Field singleton = clazz.getDeclaredField("SINGLETON");
            Object resourceManager = singleton.get(null);
            Method register = clazz.getMethod("register", Object.class, Object.class);
            register.invoke(resourceManager, "FileSystem", execConfig.fileSystem);
            print("FileSystem injected");

        } catch (Exception e) {
            error("Loading resource manager failed");
        }

        // if we are using Java In Memory File System
        if (execConfig.fileSystem == JarUtil.getJIMFS())
            JarUtil.printFileSystem(execConfig.fileSystem);

        // run the program
        JarUtil.executeMainClass(mainClass, argsList);

        // this method will never return will the program exists or terminates
    }

    public static void main(String[] args) {
        try {
            execProgram(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


}
