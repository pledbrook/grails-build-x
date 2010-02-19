package grails.build.plugin

import grails.util.BuildSettings
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class RunServletContainerTask extends DefaultTask {
    @TaskAction
    void run() {
        // The server classes are loaded from a special 'servletContainer'
        // classpath that plugins can add themselves to.
        def classpath = project.configurations.servletContainer
        classpath = classpath.files.collect { it.toURI().toURL() }

        // Load the factory class that creates the servlet container
        // starter class. First, we try the SLF4J way by simply loading
        // a fixed class from the classpath. All servlet container
        // plugins have to do is put their version of the class on the
        // 'servletContainer' classpath.
        def serverClassLoader = new URLClassLoader(classpath as URL[], getClass().classLoader)
        def serverFactoryClass
        try {
            serverFactoryClass = serverClassLoader.loadClass("grails.web.container.EmbeddableServerFactoryImpl")
        }
        catch (ClassNotFoundException ex1) {
            // Next, we try Jetty.
            try {
                serverFactoryClass = serverClassLoader.loadClass("org.grails.jetty.JettyServerFactory")
            }
            catch (ClassNotFoundException ex2) {
                // Finally, try Tomcat.
                try {
                    serverFactoryClass = serverClassLoader.loadClass("org.grails.tomcat.TomcatServerFactory")
                }
                catch (ClassNotFoundException ex3) {
                    throw new RuntimeException(
                            "[GrailsPlugin] No servlet container found on the classpath. " +
                            "Do you have Jetty, Tomcat, or some other servlet container plugin installed?")
                }
            }
        }

        // TODO Pick up configuration options from BuildConfig.groovy
        // and system/project properties.

        // This is important! The context path *must* start with a slash.
        def contextPath = project.buildData.servletContext ?: project.buildData.appName
        if (!contextPath.startsWith('/')) {
            contextPath = '/' + contextPath
        }

        // We create a new instance of whatever server factory we're using
        // and then use it to create a server instance. The server is run
        // with the project's runtime classpath, with only the system class
        // loader as its parent.
        def serverFactory = serverFactoryClass.newInstance()
        def runtimeClasspath = project.sourceSets.main.runtimeClasspath.filter { it != null }.files.collect {
            println ">> Server runtime classpath: ${it}"
            it.toURI().toURL()
        }

        // TODO Add support for custom reload settings.

        // TODO Legacy: Environment.getReloadLocationInternal() requires
        // the "base.dir" system property to be set. Argh! If it isn't,
        // Grails quietly fails to load the i18n resource bundles.
        System.setProperty(BuildSettings.APP_BASE_DIR, project.projectDir.absolutePath)

        def grailsServer = serverFactory.createInline(
                project.war.webAppDir.absolutePath,
                project.generateWebXml.targetFile.absolutePath,
                contextPath,
                new URLClassLoader(runtimeClasspath as URL[]))
        grailsServer.start null, 8080

        // We need to keep this thread alive, otherwise the server will stop.
        synchronized(grailsServer) {
            grailsServer.wait()
        }
    }
}
