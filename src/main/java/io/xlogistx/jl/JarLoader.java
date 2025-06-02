package io.xlogistx.jl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class JarLoader {

    public  static void print(String str)
    {
        System.out.println("***** JarLoader ***** " + str);
    }

    public static void error(String str)
    {
        System.err.println("***** JarLoader ***** " + str);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java JarLoader [-f] [-jar] <lib-directory> <main-class> [parmeters....]");
            System.exit(-1);
        }

        List<String> argsList = new ArrayList<>();
        for (String arg : args)
            argsList.add(arg);


        boolean mem = !"-f".equals(argsList.get(0));

        if (!mem) {
            argsList.remove(0);
        }

        boolean extractMainClass = "-jar".equals(argsList.get(0));

        if (extractMainClass) {
            argsList.remove(0);
        }


        String fatJarPath = argsList.remove(0);
        String mainClass = extractMainClass ? null : argsList.remove(0);


        try {
            //jarsURLs(libDir);
            // Extract and load JAR files
            JarUtil.ExecConfig execConfig = JarUtil.loadJars(mem, fatJarPath, extractMainClass);

            // Execute the main method of the specified class
            if (extractMainClass)
                mainClass = execConfig.mainClass;

            print("Temp lib path: " + execConfig.tempDir.toUri().toURL());
            print("Running: " + mainClass + " " + argsList);


            // if using temp file system
//            if(execConfig.tempDir.getFileSystem().equals(FileSystems.getDefault())) {
//                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//                    try
//                    {
//                        JarUtil.deleteDirectoryRecursively(execConfig.tempDir);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }));
//            }

            try
            {



                Class<?> clazz = execConfig.mainClassLoader.loadClass("org.zoxweb.shared.util.ResourceManager");
                Field singleton = clazz.getDeclaredField("SINGLETON");
                Object resourceManager = singleton.get(null);
                Method register = clazz.getMethod("register", Object.class, Object.class);
                register.invoke(resourceManager, "FileSystem", execConfig.fileSystem);
                print("FileSystem injected");

            }
            catch (Exception e)
            {
                error("Loading resource manager failed");
            }

            if(execConfig.fileSystem == JarUtil.getJIMFS())
                JarUtil.printFileSystem(execConfig.fileSystem);

            JarUtil.executeMainClass(mainClass, argsList);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
