package grails.core

import org.codehaus.groovy.grails.plugins.GrailsPluginManager
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationContext
import org.springframework.beans.BeansException
import org.codehaus.groovy.grails.plugins.DefaultGrailsPluginManager
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.plugins.PluginManagerHolder

/**
 * Created by IntelliJ IDEA.
 * User: pal20
 * Date: 15-Feb-2010
 * Time: 09:45:50
 * To change this template use File | Settings | File Templates.
 */
class GrailsPluginManagerFactoryBean implements FactoryBean, InitializingBean, ApplicationContextAware {
    Map plugins
    ApplicationContext applicationContext
    GrailsApplication application

    private GrailsPluginManager pluginManager

    public Object getObject() throws Exception { return pluginManager }
	public Class getObjectType() { return GrailsPluginManager }
	public boolean isSingleton() { return true }

	public void afterPropertiesSet() throws Exception {
        println ">> Plugins map: ${plugins.getClass()}"
        def cl = application.getClassLoader()
        def loadedPlugins = plugins.keySet().collect { cl.loadClass(it) }

        pluginManager = new DefaultGrailsPluginManager(loadedPlugins as Class[], application)
      
        pluginManager.applicationContext = applicationContext

        // TODO This stuff should be done by GrailsRuntimeConfigurator,
        // but for some reason it's creating its own plugin manager
        // instance. Hence it doesn't initialise this one. Stupid.
        pluginManager.loadPlugins()
        pluginManager.doArtefactConfiguration()
        application.initialise()

        // TODO Some code still uses PluginManagerHolder, which is just so
        // wrong. But, since it is using it, we'd better initialise it.
        PluginManagerHolder.pluginManager = pluginManager
    }

    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
