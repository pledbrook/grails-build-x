package grails.build.plugin

class InstalledSourcePlugin extends AbstractSourcePlugin {
    private String name
    private String version

    InstalledSourcePlugin(File dir) {
        super(dir)

        // Get the plugin name and version from the XML plugin descriptor.
        def xml = new XmlSlurper().parse(new File(dir, "plugin.xml"))
        name = xml.'@name'.text()
        version = xml.'@version'.text()
    }

    String getName() { return name }
    String getVersion() { return version }
}
