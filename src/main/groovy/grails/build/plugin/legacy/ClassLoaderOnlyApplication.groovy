package grails.build.plugin.legacy

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.context.ApplicationContext
import org.springframework.core.io.Resource
import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.commons.ArtefactHandler
import org.codehaus.groovy.grails.commons.ArtefactInfo

/**
 * Dummy Grails application implementation that simply provides a class
 * loader from which to load plugins. This is because certain parts of
 * Grails require a Grails application instance even though they only
 * use the class loader from it. I'm looking at you CorePluginFinder.
 *
 * For compatibility with Grails 1.2.x and earlier.
 *
 * @author Peter Ledbrook
 */
class ClassLoaderOnlyApplication implements GrailsApplication {
    private final ClassLoader classLoader

    ClassLoaderOnlyApplication(ClassLoader classLoader) {
        this.classLoader = classLoader
    }

    ClassLoader getClassLoader() {
        return classLoader
    }

    ConfigObject getConfig() { return null }
    Map getFlatConfig() { return null }
    Class[] getAllClasses() { return new Class[0] }
    Class[] getAllArtefacts() { return new Class[0] }
    ApplicationContext getMainContext() { return null }
    void setMainContext(ApplicationContext applicationContext) {}
    ApplicationContext getParentContext() { return null }
    Class getClassForName(String s) { return null }
    void refreshConstraints() {}
    void refresh() {}
    void rebuild() {}
    Resource getResourceForClass(Class aClass) { return null }
    boolean isArtefact(Class aClass) { return false }
    boolean isArtefactOfType(String s, Class aClass) { return false }
    boolean isArtefactOfType(String s, String s1) { return false }
    GrailsClass getArtefact(String s, String s1) { return null }
    ArtefactHandler getArtefactType(Class aClass) { return null }
    ArtefactInfo getArtefactInfo(String s) { return null }
    GrailsClass[] getArtefacts(String s) { return new GrailsClass[0] }
    GrailsClass getArtefactForFeature(String s, Object o) { return null }
    GrailsClass addArtefact(String s, Class aClass) { return null }
    GrailsClass addArtefact(String s, GrailsClass grailsClass) { return null }
    void registerArtefactHandler(ArtefactHandler artefactHandler) {}
    boolean hasArtefactHandler(String s) { return false }
    ArtefactHandler[] getArtefactHandlers() { return new ArtefactHandler[0] }
    void initialise() {}
    boolean isInitialised() { return false }
    Map getMetadata() { return null }
    GrailsClass getArtefactByLogicalPropertyName(String s, String s1) { return null }
    void addArtefact(Class aClass) {}
    boolean isWarDeployed() { return false }
    void addOverridableArtefact(Class aClass) {}
    void configChanged() {}
    ArtefactHandler getArtefactHandler(String s) { return null }
    void setApplicationContext(ApplicationContext applicationContext) {}
}
