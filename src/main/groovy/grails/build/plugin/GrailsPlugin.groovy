package grails.build.plugin

import groovy.xml.DOMBuilder
import org.codehaus.groovy.tools.RootLoader
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.internal.file.copy.FileCopyActionImpl

class GrailsPlugin implements Plugin<Project> {
    void use(Project project) {
        project.with {
            apply id: 'groovy'
            apply id: 'war'

            if (!project.hasProperty("grailsHome")) {
                grailsHome = System.getProperty("grails.home") ?: System.getenv("GRAILS_HOME")
            }

            // TODO Find some way of setting the environment properly.
            if (!project.hasProperty("grailsEnv")) grailsEnv = "development"

            war.webAppDir = file("web-app")

            println ">> Grails environment: ${grailsEnv}"

            // Add some standard Grails configurations to the project.
            configurations {
                // Dependencies required for compilation, but provided
                // by something else at runtime.
                provided { transitive = false }

                // The location of Grails resources, such as the template
                // project files.
                resources

                // The dependencies required to start the servlet container
                // for the 'run' task.
                servletContainer
            }

            // A Grails project has a rather distinctive directory structure,
            // but it is at heart a straight WAR project. This sets up the
            // source directories.
            sourceSets {
                main {
                    groovy {
                        def grailsAppDirs = file("grails-app").listFiles({ f -> f.directory } as FileFilter)
                        srcDirs grailsAppDirs, "src/groovy", "src/java"
                        compileClasspath += configurations.provided
                    }
                }
            }

            dependencies {
                groovy  "org.codehaus.groovy:groovy:1.7.0"
                compile "org.grails:grails-bootstrap:1.2.0"

                // Required by Grails' AST transformations.
                provided "commons-lang:commons-lang:2.4",
                         "javax.servlet:servlet-api:2.5",
                         "log4j:log4j:1.2.14",
                         "org.grails:grails-spring:1.2.0",
                         "org.slf4j:slf4j-api:1.5.8",
                         "org.slf4j:slf4j-jdk14:1.5.8",
                         "org.slf4j:jcl-over-slf4j:1.5.8",
                         "org.springframework:spring-core:3.0.0.RELEASE",
                         "org.springframework:spring-web:3.0.0.RELEASE"

                // Insert the plugin as a runtime dependency so that
                // the project can use its custom classes (that effectively
                // override the standard Grails ones).
                def r = getClass().getResource("GrailsPlugin.class")
                if (r.protocol == "jar") {
                    def path = r.toString() - "jar:"
                    path = path[0..<(path.indexOf('!'))]

                    runtime files(new URL(path).file)
                }

                // Add Grails resources to the classpath so that we can
                // load them from there. Ideally, 'grailsHome' wouldn't
                // be added, but unfortunately the current directory
                // structure of Grails' home and the grails-resources JAR
                // mean that it's necessary.
                if (grailsHome) {
                    resources files(grailsHome)
                }
                resources "org.grails:grails-resources:1.2.0"
            }

            task("tmpBuildDir") {
                dir = new File(buildDir, "tmp")
                doLast {
                    ant.mkdir dir: dir
                }
            }

            // Almost all tasks depend on information about the build. This
            // task loads it up.
            createBuildDataTask(project)
            createBuildPluginsTask(project, [ buildData ])
            createCopyWebXmlTemplateTask(project, [ buildData, buildPlugins, tmpBuildDir ])
            createGenerateWebXmlTask(project, [ copyWebXmlTemplate ])
            createGenerateApplicationContextTask(project, [ buildData, buildPlugins ])
            createPackageI18nTask(project, [ buildData ])
            createRunTask(project, [ compileGroovy, processResources ])

            compileGroovy.source sourceSets.main.groovy
            compileGroovy.source projectDir.listFiles({ f ->
                f.name ==~ /\w+GrailsPlugin.groovy/ } as FileFilter)

            // The i18n and JSP files have to be included in the runtime
            // classpath, so we add the 'resources' directory now.
            sourceSets.main.runtimeClasspath += files(packageI18n.destinationDir)

            // Configure some of the tasks provided by other plugins. The
            // compile steps require the Grails plugins to be built first.
            compileJava.dependsOn buildPlugins
            processResources.dependsOn generateWebXml, generateApplicationContextXml, packageI18n

            war {
                from webAppDir
                webXml = generateWebXml.targetFile
            }

        }
    }

    private createBuildDataTask(project) {
        project.with {
            task("buildData") {
                // Start by loading the application metadata from
                // application.properties.
                def metadata = new Properties()
                metadata.load(file("application.properties").newReader())

                appName = metadata.getProperty("app.name")
                appVersion = metadata.getProperty("app.version")
                servletContext = metadata.getProperty("app.context")
                servletVersion = metadata.getProperty("app.servlet.version")

                // Next, load up the build settings, which involves
                // loading and parsing BuildConfig.groovy. The root
                // loader is set to the build classpath, which has
                // far fewer JARs than the old Grails build system
                // classpath.
                settings = new grails.util.BuildSettings(grailsHome ? new File(grailsHome) : null, projectDir)
                settings.rootLoader = getClass().classLoader
                settings.loadConfig()

                // TODO Legacy stuff! Code should not be picking up build settings
                // from this static variable.
                grails.util.BuildSettingsHolder.settings = settings
            }
        }
    }

    private createBuildPluginsTask(project, dependsOn) {
        project.task("buildPlugins", type: GrailsPluginsTask, dependsOn: dependsOn)
    }

    private createCopyWebXmlTemplateTask(project, dependsOn) {
        project.with {
            task("copyWebXmlTemplate", dependsOn: dependsOn) {
                grailsWarTemplatesPath = "src/war/WEB-INF"
                localWarTemplatesPath = "src/templates/war"
                template = "web.xml"
                destFile = new File(tmpBuildDir.dir, "web.xml.tmp")

                doLast {
                    def templateStream
                    def localTemplate = file("${localWarTemplatesPath}/${template}")
                    if (localTemplate.exists()) {
                        // Copy the local template web descriptor.
                        localTemplate.newInputStream()
                    }
                    else {
                        template = "web${buildData.servletVersion}.template.xml"

                        // Copy the file from the classpath.
                        def cl = new RootLoader(
                                configurations.resources.files.collect { it.toURI().toURL() } as URL[],
                                ClassLoader.getSystemClassLoader())
                        templateStream = cl.getResource("${grailsWarTemplatesPath}/${template}").openStream()
                    }

                    // Perform the copy.
                    def buf = new byte[8192]
                    templateStream.withStream { input ->
                        destFile.withOutputStream { output ->
                            for (int bytesRead = input.read(buf); bytesRead != -1; bytesRead = input.read(buf)) {
                                output.write(buf, 0, bytesRead)
                            }
                        }
                    }
                }
            }
        }
    }

    private createGenerateWebXmlTask(project, dependsOn) {
        project.with {
            task("generateWebXml", type: org.gradle.api.tasks.Copy, dependsOn: dependsOn) {
                targetFile = file("web-app/WEB-INF/web.xml")
                from(new File(tmpBuildDir.dir, "web.xml.tmp")) {
                    rename "web.xml.tmp", targetFile.name

                    // Can't use standard Ant filter here, because the metadata
                    // isn't resolved until execution time. This is also the reason
                    // we're using closures in the GString.
                    filter { line ->
                        line.replace("@grails.project.key@", "${->buildData.appName}-${grailsEnv}-${->buildData.appVersion}")
                    }
                }
                into targetFile.parentFile

                doLast {
                    // So we have the temporary template web descriptor. Now we need
                    // to pass it to the plugins for completion.
                    def dom = DOMBuilder.parse(targetFile.newReader())
                    use(GrailsDOMCategory) {
                        buildPlugins.pluginManager.processWebDescriptor(dom.documentElement)
                    }

                    // Finally, write the updated web descriptor back to file.
                    writeDomToFile(dom, targetFile)
                }
            }
        }
    }

    private createGenerateApplicationContextTask(project, dependsOn) {
        project.with {
            task("generateApplicationContextXml", dependsOn: dependsOn) {
                template = "applicationContext.xml"
                destFile = new File(war.webAppDir, "WEB-INF/applicationContext.xml")

                doLast {
                    // Start by loading the template XML file into a DOM tree.
                    def dom
                    def localTemplate = file("${copyWebXmlTemplate.localWarTemplatesPath}/${template}")
                    if (localTemplate.exists()) {
                        // Copy the local template bean descriptor file.
                        dom = DOMBuilder.parse(localTemplate.newReader())
                    }
                    else {
                        // Copy the file from the classpath.
                        def cl = new RootLoader(
                                configurations.resources.files.collect { it.toURI().toURL() } as URL[],
                                ClassLoader.getSystemClassLoader())
                        def template = cl.getResource("${copyWebXmlTemplate.grailsWarTemplatesPath}/${template}")
                        dom = DOMBuilder.parse(template.openStream().newReader())
                    }

                    // Add the generated bean definitions for 'grailsApplication'
                    // and 'pluginManager' to the DOM.
                    use(GrailsDOMCategory) {
                        // First replace the 'pluginManager' bean definition.
                        def bean = dom.documentElement.'**'.bean.find { it.'@id' == "pluginManager"}

                        def b = new DOMBuilder(dom);
                        def newNode = b.bean(id: "pluginManager", class: "grails.core.GrailsPluginManagerFactoryBean") {
                            property(name: "application", ref: "grailsApplication")
                            property(name: "plugins") {
                                map {
                                    buildPlugins.pluginManager.sourcePlugins.each { plugin ->
                                        entry(
                                                key: plugin.pluginDescriptor.class.name,
                                                value: plugin.pluginDirectory.absolutePath)
                                    }
                                }
                            }
                        }
                        bean.parentNode.replaceChild(newNode, bean)

                        // Next, the 'grailsResourceHolder' one.
                        bean = dom.documentElement.'**'.bean.find { it.'@id' == "grailsResourceHolder" }
                        newNode = b.bean(
                                id: "grailsResourceHolder",
                                scope: "prototype",
                                class: "org.codehaus.groovy.grails.commons.spring.GrailsResourceHolder") {
                            property(name: "resources", value: "file:${projectDir}/grails-app/**/*.groovy")
                        }
                        bean.parentNode.replaceChild(newNode, bean)
                    }

                    // Finally, write the modified DOM to the destination.
                    writeDomToFile(dom, destFile)
                }
            }
        }
    }

    /**
     * Creates a task that copies all the project's message bundles to
     * build/resources. If 'native2ascii' is enabled, then that tool is
     * used to convert the files from UTF-8 to ASCII en route.
     */
    private createPackageI18nTask(project, dependsOn) {
        project.task("packageI18n", type: Copy, dependsOn: dependsOn) {
            def buildConfig = project.buildData.settings.config
            if (buildConfig.grails.enable.native2ascii) {
                copyAction = new FileCopyActionImpl(project.fileResolver, new Native2AsciiCopySpecVisitor())
            }

            from(".") {
                include "grails-app/i18n/**/*.properties"
            }
            into new File(project.buildDir, "resources")
        }
    }

    private createRunTask(project, dependsOn) {
        project.task("run", type: RunServletContainerTask, dependsOn: dependsOn)
    }

    private void writeDomToFile(dom, destFile) {
        def transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
        transformer.transform(
                new javax.xml.transform.dom.DOMSource(dom),
                new javax.xml.transform.stream.StreamResult(destFile))
    }
}
