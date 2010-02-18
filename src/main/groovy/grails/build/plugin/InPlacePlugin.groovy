package grails.build.plugin

import grails.util.BuildSettings
import grails.util.GrailsNameUtils

class InPlacePlugin extends AbstractSourcePlugin {
    private String name
    private String version

    InPlacePlugin(File dir, BuildSettings settings) {
        super(dir)

        // The name may be specified in one of two ways. Newer plugins
        // put it in BuildConfig.groovy under the key "app.name". Older
        // ones get their name from the plugin descriptor class name.
        def configFile = new File(dir, "grails-app/conf/BuildConfig.groovy")
        if (configFile.exists()) {
            // Load the config file so we can check whether it has an
            // "app.name" entry.
            def pluginSettings = new BuildSettings(settings.grailsHome, dir)
            pluginSettings.rootLoader = settings.rootLoader

            // TODO Legacy code: we have to make sure that the project 'plugins'
            // directory exists, otherwise Grails 1.2 and earlier bomb when loading
            // the build configuration.
            pluginSettings.projectPluginsDir.mkdirs()
            // End legacy

            pluginSettings.loadConfig()

            this.name = pluginSettings.config.app.name ?: null
            this.version = pluginSettings.config.app.version ?: null
        }

        // Get the plugin name from the plugin descriptor class if we
        // haven't got it from BuildConfig.groovy.
        if (!this.name) {
            def pluginDescriptor = dir.listFiles().find { it.name.endsWith("GrailsPlugin.groovy")}
            
            if (!pluginDescriptor) {
                throw new RuntimeException("[GrailsPlugin] No plugin descriptor found in ${dir}")
            }

            this.name = GrailsNameUtils.getPluginName(pluginDescriptor.name)
        }
    }

    String getName() { return name } 
    String getVersion() { return version }
}
