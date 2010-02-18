package grails.build.plugin

import grails.util.GrailsNameUtils

/**
 * Created by IntelliJ IDEA.
 * User: pal20
 * Date: 12-Feb-2010
 * Time: 19:47:41
 * To change this template use File | Settings | File Templates.
 */
class CorePlugin extends AbstractPlugin {
    private final String name
    private final String version
    private final pluginDescriptor
    private Set<File> runtimeClasspath

    CorePlugin(Class pluginClass) {
        name = GrailsNameUtils.getScriptName(pluginClass.simpleName - "GrailsPlugin")
        pluginDescriptor = pluginClass.newInstance()
        version = pluginDescriptor.version
    }

    String getName() { return name }
    String getVersion() { return version }
    boolean isSourcePlugin() { return false }
    File getPluginDirectory() { return null }
    Set<File> getRuntimeClasspath() { return runtimeClasspath }
    void setRuntimeClasspath(Set<File> paths) { this.runtimeClasspath = paths }

    String toString() { "CorePlugin ${name}-${version}"}

    protected getPluginDescriptor() {
        return this.pluginDescriptor
    }
}
