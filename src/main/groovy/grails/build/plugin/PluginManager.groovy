package grails.build.plugin

import grails.build.plugin.legacy.ClassLoaderOnlyApplication
import org.codehaus.groovy.grails.plugins.CorePluginFinder
import org.gradle.api.Project

class PluginManager {
    Project project
    def settings
    Set<Plugin> sourcePlugins
    List<Plugin> sortedPlugins

    PluginManager(Project project, settings) {
        this.project = project
        this.settings = settings
    }

    Set<Plugin> getSourcePlugins() {
        if (!sourcePlugins) {
            // First, resolve the in-place plugins.
            def inPlacePluginPaths = settings.config.grails.plugin.location.findAll { it.value }
            def inPlacePlugins = inPlacePluginPaths.collect { configOption ->
                // The path may be relative or absolute. If it's relative,
                // we need to make sure it's relative to the project directory.
                def dir = new File(configOption.value)
                if (!dir.absolute) {
                    dir = new File(project.projectDir, configOption.value)
                }
                return new InPlacePlugin(dir, settings)
            }

            // Now for installed source plugins, i.e. traditional Grails
            // plugins that contain source files rather than compiled classes.
            def metadata = loadMetadata()
            def installedPluginNames = metadata.keySet().grep(~/plugins\..+/).collect { key ->
                // Plugin name is formed from the base name and the plugin version.
                def baseName = key - "plugins."
                def version = metadata.getProperty(key)
                return "${baseName}-${version}"
            }

            def installedPlugins = installedPluginNames.collect {
                new InstalledSourcePlugin(new File(settings.projectPluginsDir, it))
            }

            sourcePlugins = new HashSet<Plugin>()
            sourcePlugins.addAll(inPlacePlugins)
            sourcePlugins.addAll(installedPlugins)
        }

        return Collections.unmodifiableSet(sourcePlugins)
    }

    def processWebDescriptor(xml) {
        ensurePluginsAreLoadedAndSorted()
        sortedPlugins.each { Plugin p ->
            p.doWithWebDescriptor(xml)
        }
    }

    private ensurePluginsAreLoadedAndSorted() {
        if (sortedPlugins) return

        sortedPlugins = resolveCorePlugins() + sourcePlugins
        Collections.sort(sortedPlugins, new PluginLoadOrderComparator(sortedPlugins))
    }

    private resolveCorePlugins() {
        // TODO This is a legacy approach for versions of Grails
        // in which CorePluginFinder requires a GrailsApplication
        // instance. From 1.3 onwards, we can pass a class loader
        // directly.
        //
        // TODO The CorePluginFinder loads classes from the thread
        // context class loader. Again, this is a legacy issue due
        // to a bug in CorePluginFinder. Fixed from 1.3 onwards.
        def oldContextClassLoader = Thread.currentThread().contextClassLoader
        def runtimeClasspath = project.sourceSets.main.runtimeClasspath.filter { it != null }.files.collect {it.toURI().toURL() }

        def classLoader = new URLClassLoader(runtimeClasspath as URL[], getClass().classLoader)
        Thread.currentThread().contextClassLoader = classLoader
        def coreFinder = new CorePluginFinder(new ClassLoaderOnlyApplication(classLoader))
        Thread.currentThread().contextClassLoader = oldContextClassLoader

        println "Searching for core plugins..."

        return coreFinder.pluginClasses.
                findAll { !(it.simpleName in [ "GrailsPlugin", "AbstractGrailsPlugin", "DefaultGrailsPlugin" ]) }.
                collect { new CorePlugin(it) }
    }

    private Properties loadMetadata() {
        def metadata = new Properties()
        metadata.load(new File(project.projectDir, "application.properties").newReader())
        return metadata
    }
}
