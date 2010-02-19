package grails.build.plugin

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.CopyAction
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.internal.file.copy.EmptyCopySpecVisitor

/**
 * A CopySpec visitor that copies files from one location to another,
 * converting them from UTF-8 to Latin-1 in the process. This is
 * particularly useful for i18n resource bundles.
 */
class Native2AsciiCopySpecVisitor extends EmptyCopySpecVisitor {
    private File baseDestDir
    private boolean didWork

    // The native2ascii instance that will perform the conversion.
    private n2a

    Native2AsciiCopySpecVisitor() {
        // First, find the "tools.jar" file wherever the JDK is installed.
        // First assume that the "java.home" system property points to a
        // JRE within a JDK.
        def javaHome = System.getProperty("java.home");
        def toolsJar = new File(javaHome, "../lib/tools.jar");
        if (!toolsJar.exists()) {
            // The "tools.jar" cannot be found with that path, so
            // now try with the assumption that "java.home" points
            // to a JDK.
            toolsJar = new File(javaHome, "tools.jar");
        }

        // Note that on Mac OS X, native2ascii is on the system classpath,
        // and tools.jar does not exist. Hence we check whether tools.jar
        // exists before attempting to load the main native2ascii class.
        def cl = toolsJar.exists() ?
                new URLClassLoader([ toolsJar.toURI().toURL() ] as URL[]) :
                ClassLoader.systemClassLoader
        n2a = cl.loadClass("sun.tools.native2ascii.Main").newInstance()
    }

    void startVisit(CopyAction action) {
        baseDestDir = action.destinationDir
        if (baseDestDir == null) {
            throw new InvalidUserDataException(
                    "No copy destination directory has been specified. " +
                    "Use 'into' to specify a target directory.")
        }
    }

    void visitFile(FileVisitDetails source) {
        File target = source.relativePath.getFile(baseDestDir)
        copyAndConvertFile(source, target)
    }

    boolean getDidWork() {
        return didWork;
    }

    void copyAndConvertFile(FileTreeElement srcFile, File destFile) {
        destFile.parentFile.mkdirs()
        didWork = native2ascii(srcFile.file, destFile)
    }

    boolean native2ascii(File srcFile, File destFile) {
        return n2a.convert([ "-encoding", "UTF-8", srcFile.absolutePath, destFile.absolutePath ] as String[])
    }
}
