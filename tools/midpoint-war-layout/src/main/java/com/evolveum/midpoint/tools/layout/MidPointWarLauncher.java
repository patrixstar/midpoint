/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.tools.layout;

import org.springframework.boot.loader.WarLauncher;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.jar.JarFile;

import java.io.File;
import java.util.List;

/**
 * Created by Viliam Repan (lazyman).
 */
public class MidPointWarLauncher extends WarLauncher {

    private static volatile MidPointWarLauncher warlauncher = null;
    private static volatile ClassLoader classLoader = null;

    public MidPointWarLauncher() {
    }

    public MidPointWarLauncher(Archive archive) {
        super(archive);
    }

    public static void main(String[] args) throws Exception {
        String mode = args != null && args.length > 0 ? args[0] : null;

         if ("start".equals(mode)) {
             MidPointWarLauncher.start(args);
         } else if ("stop".equals(mode)) {
             MidPointWarLauncher.stop(args);
         } else {
             new MidPointWarLauncher().launch(args);
         }
    }

    public static synchronized void start(String[] args) throws Exception {
        warlauncher = new MidPointWarLauncher();

        try {
            JarFile.registerUrlProtocolHandler();
            classLoader = warlauncher.createClassLoader(warlauncher.getClassPathArchives());
            warlauncher.launch(args, warlauncher.getMainClass(), classLoader, true);
        } catch (Exception ex) {
            StringBuilder sb = new StringBuilder();
            sb.append("Could not start MidPoint application").append(";").append(ex.getLocalizedMessage());
            throw new Exception(sb.toString(), ex);
        }
    }

    public static synchronized void stop(String[] args) throws Exception {

        try {
            if (warlauncher != null) {
                warlauncher.launch(args, warlauncher.getMainClass(), classLoader, true);
                warlauncher = null;
                classLoader = null;
            }
        } catch (Exception ex) {
            StringBuilder sb = new StringBuilder();
            sb.append("Could not stop MidPoint application").append(";").append(ex.getLocalizedMessage());
            throw new Exception(sb.toString(), ex);

        }
    }

    protected void launch(String[] args, String mainClass, ClassLoader classLoader, boolean wait) throws Exception {

        Thread.currentThread().setContextClassLoader(classLoader);

        Thread runnerThread = new Thread(() -> {
            try {
                createMainMethodRunner(mainClass, args, classLoader).run();
            } catch (Exception ex) {

            }
        });
        runnerThread.setContextClassLoader(classLoader);
        runnerThread.setName(Thread.currentThread().getName());
        runnerThread.start();
        if (wait) {
            runnerThread.join();
        }

    }

    @Override
    protected List<Archive> getClassPathArchives() throws Exception {
        List<Archive> archives = super.getClassPathArchives();

        File midPointHomeLib = getMidPointHomeLib();
        if (midPointHomeLib == null || !midPointHomeLib.exists() || !midPointHomeLib.isDirectory()) {
            return archives;
        }

        File[] files = midPointHomeLib.listFiles(file -> file.getName().toLowerCase().endsWith(".jar"));
        if (files == null) {
            return archives;
        }

        for (File file : files) {
            archives.add(new JarFileArchive(file));
        }

        return archives;
    }

    private File getMidPointHomeLib() {
        String midPointHome = System.getProperty("midpoint.home");
        return new File(midPointHome, "lib");
    }
}
