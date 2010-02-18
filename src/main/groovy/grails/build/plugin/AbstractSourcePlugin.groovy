package grails.build.plugin

abstract class AbstractSourcePlugin extends AbstractPlugin {
    private final File dir
    private Set<File> runtimeClasspath
    private pluginDescriptor

    AbstractSourcePlugin(File dir) {
        this.dir = dir
    }

    boolean isSourcePlugin() { return true }
    File getPluginDirectory() { return this.dir }
    Set<String> getLoadedAfter() { return Collections.emptySet() }
    Set<String> getLoadedBefore() { return Collections.emptySet() }
    Set<File> getRuntimeClasspath() { return runtimeClasspath }
    void setRuntimeClasspath(Set<File> paths) { this.runtimeClasspath = paths }

    String toString() {
        return "${getClass().simpleName} [${name} - ${version}]"
    }

    protected getPluginDescriptor() {
        if (pluginDescriptor) return pluginDescriptor

        if (!runtimeClasspath) {
            throw new IllegalStateException(
                    "Runtime classpath not initialised for plugin ${name}, " +
                    "so cannot load plugin descriptor.")
        }

        // To load the plugin descriptor, we need to know the class
        // name. We do that by looking in the plugin's directory for
        // the *GrailsPlugin.groovy file.
        def pluginDescriptorFile = dir.listFiles().find { it.name.endsWith("GrailsPlugin.groovy")}
        if (!pluginDescriptorFile) {
            throw new RuntimeException("[GrailsPlugin] No plugin descriptor found in ${dir}")
        }

        // Load the plugin descriptor from the runtime classpath.
        def cl = new URLClassLoader(
                runtimeClasspath.collect { it.toURI().toURL() } as URL[],
                getClass().classLoader)
        pluginDescriptor = cl.loadClass(pluginDescriptorFile.name - ".groovy").newInstance()
    }
}
