package io.xlogistx.jl;

import java.util.ArrayList;
import java.util.List;

public class JarLoader {


    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java JarLoader [-f] [-jar] <lib-directory> <main-class> [parmeters....]");
            System.exit(1);
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

            System.out.println("Temp lib path: " + execConfig.tempDir.toUri().toURL());
            System.out.println("Running: " + mainClass + " " + argsList);


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


            JarUtil.executeMainClass(mainClass, argsList);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
