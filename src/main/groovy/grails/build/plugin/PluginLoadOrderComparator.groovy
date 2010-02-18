package grails.build.plugin

/**
 * Created by IntelliJ IDEA.
 * User: pal20
 * Date: 13-Feb-2010
 * Time: 07:45:19
 * To change this template use File | Settings | File Templates.
 */
class PluginLoadOrderComparator implements Comparator<Plugin> {
    private Map<String, Plugin> pluginMap

    PluginLoadOrderComparator(Collection<Plugin> plugins) {
        pluginMap = [:]
        plugins.each { p -> pluginMap[p.name] = p}
    }

    int compare(Plugin o1, Plugin o2) {
        if (o1 == null || o2 == null) {
            throw new IllegalArgumentException("At least one of the comparison objects is null.")
        }
        
        // First check the load afters.
        boolean loadedAfter = isLoadedAfter(o1, o2.name)
        if (!loadedAfter) {
            // We also need to check whether o2 is loaded before o1.
            // It's the same thing looked at in the other direction.
            loadedAfter = isLoadedBefore(o2, o1.name)
        }

        // Now check the load befores.
        boolean loadedBefore = isLoadedBefore(o1, o2.name)
        if (!loadedBefore) {
            // We also need to check whether o2 is loaded before o1.
            // It's the same thing looked at in the other direction.
            loadedBefore = isLoadedAfter(o2, o1.name)
        }

        // If o1 is loaded both before *and* after o2, we have a circular
        // dependency.
        if (loadedAfter && loadedBefore) {
            throw new IllegalStateException(
                    "Circular dependency found between plugins ${o1.name} and ${o2.name}.")
        }

        return loadedAfter ? 1 : (loadedBefore ? -1 : 0)
    }

    private boolean isLoadedAfter(plugin, refPluginName) {
        if (refPluginName in plugin.loadedAfter) return true

        boolean loadedAfter = false
        for (String pluginName in plugin.loadedAfter) {
            loadedAfter = isLoadedAfter(pluginMap[pluginName], refPluginName)
            if (loadedAfter) break;
        }

        return loadedAfter
    }

    private boolean isLoadedBefore(plugin, refPluginName) {
        if (refPluginName in plugin.loadedBefore) return true

        boolean loadedBefore = false
        for (String pluginName in plugin.loadedBefore) {
            loadedBefore = isLoadedBefore(pluginMap[pluginName], refPluginName)
            if (loadedBefore) break;
        }

        return loadedBefore
    }
}
