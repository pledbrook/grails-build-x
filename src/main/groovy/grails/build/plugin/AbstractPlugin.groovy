package grails.build.plugin

import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * Created by IntelliJ IDEA.
 * User: pal20
 * Date: 13-Feb-2010
 * Time: 16:07:41
 * To change this template use File | Settings | File Templates.
 */
abstract class AbstractPlugin implements Plugin {
    private loadedAfter
    private loadedBefore

    Set<String> getLoadedAfter() {
        if (loadedAfter == null) {
            loadedAfter = new HashSet<String>()
            def pd = pluginDescriptor
            if (pd.metaClass.hasProperty(pd, "loadAfter")) {
                loadedAfter.addAll(pd.loadAfter)
            }
        }
        return loadedAfter
    }

    Set<String> getLoadedBefore() {
        if (loadedBefore == null) {
            loadedBefore = new HashSet<String>()
            def pd = pluginDescriptor
            if (pd.metaClass.hasProperty(pd, "loadBefore")) {
                loadedBefore.addAll(pd.loadBefore)
            }
        }
        return loadedBefore
    }

    void doWithWebDescriptor(xml) {
        println ">> doWithWebDescriptor for plugin ${name}"
        def pd = pluginDescriptor
        initWatchedResources(pd)

        if (pd.metaClass.hasProperty(pd, "doWithWebDescriptor")) {
            pluginDescriptor.doWithWebDescriptor(xml)
        }
    }

    void initWatchedResources(pluginDescriptor) {
        if (pluginDescriptor.metaClass.hasProperty(pluginDescriptor, "watchedResources")) {
            def resolver = new PathMatchingResourcePatternResolver()
            def resourcePatterns = pluginDescriptor.watchedResources
            if (resourcePatterns instanceof CharSequence) resourcePatterns = [ resourcePatterns ]

            // TODO Convert the resource patterns to actual resources.
            // To see what the precise behaviour should be, see
            // DefaultGrailsPlugin.evaluateOnChangeListeners().
            def resources = []
            resourcePatterns.each { resources.addAll(Arrays.asList(resolver.getResources(it))) }

            pluginDescriptor.watchedResources = resources as Resource[]
        }
    }

    abstract protected getPluginDescriptor();

    private String scrubResourcePath(String resourcePath, String baseLocation) {
        String location = baseLocation;
        if(!location.endsWith(File.separator)) location = location + File.separator;
        if(resourcePath.startsWith(".")) resourcePath = resourcePath.substring(1);
        else if(resourcePath.startsWith("file:./")) resourcePath = resourcePath.substring(7);
        resourcePath = "file:"+location + resourcePath;
        return resourcePath;
    }
}
