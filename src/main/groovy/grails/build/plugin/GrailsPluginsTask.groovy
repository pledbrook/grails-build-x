package grails.build.plugin

import org.gradle.BuildResult
import org.gradle.CacheUsage
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.TaskAction
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.initialization.DefaultCacheInvalidationStrategy
import org.gradle.initialization.DefaultGradleLauncherFactory
import org.gradle.util.GFileUtils
import groovy.text.SimpleTemplateEngine

class GrailsPluginsTask extends AbstractTask {
    @TaskAction
    void loadPlugins() {
        // Get the standard build script for Grails plugins from the
        // classpath and load it as a template.
        def pluginScriptTemplate = new SimpleTemplateEngine().createTemplate(
                getClass().getResource("plugin.gradle").text)

        // The Grails plugins will need to know something about the build
        // so we grab the Grails build settings.
        def settings = project.buildData.settings
        logger.debug """\
Grails settings: Base dir = ${settings.baseDir}
                 Grails home = ${settings.grailsHome}
                 Grails version: ${settings.grailsVersion}"""

        // We keep track of the plugins we've loaded in this map. The
        // keys are the plugin names and the values are the Gradle
        // projects for those plugins.
        project.grailsPlugins = [:]

        // Build all the source-based plugins. Some magic Gradle caching
        // mechanism is used to skip plugins that haven't changed. It's
        // based on the code Gradle uses to handle 'buildSrc'.
        pluginManager = new PluginManager(project, settings)
        for (plugin in pluginManager.sourcePlugins) {
            logger.info "================================================ Start building ${plugin.name}"
            logger.info "Plugin directory: ${plugin.pluginDirectory}"

            // Initial configuration for the Gradle build we're going to launch.
            def buildParameters = project.gradle.startParameter.newBuild()
            buildParameters.currentDir = plugin.pluginDirectory
            buildParameters.searchUpwards = false
            buildParameters.taskNames = [ "exportedClasses" ]
            boolean executeBuild = true

            // Don't bother building the project if nothing's changed.
            File markerFile = new File(buildParameters.currentDir, "build/COMPLETED")
            if (buildParameters.cacheUsage == CacheUsage.ON
                    && new DefaultCacheInvalidationStrategy().isValid(markerFile, buildParameters.currentDir)) {
                executeBuild = false
            }

            // If the plugin has a 'build.gradle' file, use that, otherwise
            // use a standard build script packaged with this Gradle plugin.
            def metadata = this.loadMetadata(plugin.pluginDirectory)
            def defaultBuildFile = new File(plugin.pluginDirectory, Project.DEFAULT_BUILD_FILE)
            if (!defaultBuildFile.isFile()) {
                logger.debug "Gradle build file not found. Using default one instead."

                // The standard plugin build script has some placeholders
                // that we need to fill in. So we run it though a template
                // engine first.
                def script = pluginScriptTemplate.make(
                        grailsHome: settings.grailsHome,
                        grailsVersion: metadata.'app.grails.version',
                        inPlacePlugin: plugin instanceof InPlacePlugin)

                buildParameters.buildScriptSource = new StringScriptSource(
                        "Standard Grails plugin build script",
                        script.toString())
            }

            // Now we build the plugin by creating a new Gradle launcher
            // to execute the build in the plugin's directory.
            def gradleLauncher = new DefaultGradleLauncherFactory().newInstance(buildParameters)

            BuildResult buildResult
            if (executeBuild) {
                buildResult = gradleLauncher.run()
            }
            else {
                // If we don't need to build the plugin, we still need its
                // dependencies and classpaths.
                buildResult = gradleLauncher.getBuildAnalysis()
            }
            buildResult.rethrowFailure()
            GFileUtils.touch(markerFile)

            // Make the plugin's project instance available from the root
            // project. Then, if the Grails plugin has a Gradle Plugin
            // instance, it can access the classpaths. Otherwise, the
            // Gradle plugin has no way of finding out about its own project.
            def pluginProject = buildResult.gradle.rootProject
            project.grailsPlugins[plugin.name] = pluginProject

            // Find out whether the plugin implements a Gradle plugin. If
            // it does, execute it now.
            //
            // Load the plugin's BuildConfig.groovy file to find out whether
            // there is an associated Gradle plugin.
            def pluginConfig = loadBuildConfig(pluginProject.projectDir, metadata, settings)
            if (pluginConfig?.plugin?.buildPluginClass) {
                // We will load the plugin within the context of the plugin's
                // runtime classpath.
                def pluginBuildClasspath = buildResult.gradle.rootProject.sourceSets.plugin.
                        runtimeClasspath.filter { it != null }.files

                def cl = new URLClassLoader(
                        pluginBuildClasspath.collect { it.toURI().toURL() } as URL[],
                        getClass().classLoader)

                try {
                    // Load and (manually) execute the Gradle plugin.
                    def buildPlugin = cl.loadClass(pluginConfig.plugin.buildPluginClass).newInstance()
                    buildPlugin.use(project)
                }
                catch (ClassNotFoundException ex) {
                    throw new RuntimeException("[GrailsPlugin] Can't find configured build plugin " +
                            "${pluginConfig.plugin.buildPluginClass}. Please check your " +
                            "plugin.buildPluginClass setting.")
                }
            }

            // Pass the plugin's runtime classpath to the plugin itself.
            // This allows the plugin to load the plugin descriptor class.
            def pluginRuntimeClasspath = buildResult.gradle.rootProject.sourceSets.main.
                    runtimeClasspath.filter { it != null }
            plugin.runtimeClasspath = pluginRuntimeClasspath.files

            // Add the plugin's runtime classpath to the project's so that
            // the application can load them.
            //
            // TODO Work out why we have to filter for null. May be a bug in Gradle.
            //
            // TODO Only runtime plugins should be added to the project's
            // runtime classpath. No point having Jetty on the classpath for
            // example.
            project.dependencies {
                runtime pluginRuntimeClasspath
            }

            logger.debug("Gradle source classpath is: {}", pluginRuntimeClasspath.files)
            logger.info "================================================ Finished building ${plugin.name}"
        }
    }

    private loadMetadata(baseDir) {
        def metadata = new Properties()
        metadata.load(new File(baseDir, "application.properties").newReader())
        return metadata
    }

    private loadBuildConfig(File baseDir, metadata, settings) {
        // Now prepare a slurper to load the BuildConfig.groovy. This
        // involves add various properties to the binding so that the
        // BuildConfig.groovy file has access to them.
        def slurper = new ConfigSlurper()
        slurper.setBinding(
                basedir: baseDir.path,
                baseFile: baseDir,
                baseName: baseDir.name,
                grailsHome: settings.grailsHome?.path,
                grailsVersion: settings.grailsVersion,
                userHome: settings.userHome,
                grailsSettings: settings,
                appName: metadata.'app.name',
                appVersion: metadata.'app.version')

        // Finally, load the BuildConfig.groovy file.
        // TODO Make the relative path to build config file a static
        // constant.
        def pluginConfigFile = new File(baseDir, "grails-app/conf/BuildConfig.groovy")
        if (pluginConfigFile.exists()) {
            return slurper.parse(pluginConfigFile.toURI().toURL())
        }
        else {
            return null
        }
    }
}
