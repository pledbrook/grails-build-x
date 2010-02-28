package grails.build

import static org.hamcrest.Matchers.startsWith
import org.testng.annotations.Test
import org.testng.annotations.BeforeMethod
import org.testng.annotations.AfterMethod

/**
 *
 */
class InitTest extends AbstractIntegrationTest {
    private static final String TEST_PROJECT_NAME = "dummy"

    File projectDir

    @BeforeMethod
    void createTempDir() {
        projectDir = new File("build/test-projects/${TEST_PROJECT_NAME}")
        projectDir.mkdirs()
    }

    @AfterMethod
    void deleteTempDir() {
        projectDir.deleteDir()
    }

    @Test
    void createApplicationWithExplicitVersion() {
        def testScript = """\
buildscript {
    repositories {
        mavenCentral()
        mavenRepo urls: "http://download.java.net/maven/2/"
        flatDir dirs: "${new File('build/libs').absolutePath}"
    }

    dependencies {
        classpath "org.grails:grails-bootstrap:1.2.0",
                  "org.grails:grails-core:1.2.0",
                  ":grails-build:"
    }
}

apply id: 'grails-x'

version = "1.0-SNAPSHOT"
servletVersion = "2.4"

dependencies {
    compile "org.grails:grails-core:1.2.0"
}
"""
        def buildFile = testFile(projectDir, "build.gradle")
        buildFile.write testScript

        def result = usingProjectDir(projectDir).withTasks("init").run()
        if (result.error) {
            println "Error\n-----\n${result.error}"
            assert false
        }

        // Check the common project directories and files.
        verifyProjectStructure()

        // There shouldn't be a plugin descriptor.
        assert projectDir.listFiles().find { it.name.endsWith("GrailsPlugin.groovy") } == null

        // Check that the application metadata is initialised correctly.
        def metadata = new Properties()
        metadata.load(new File(projectDir, "application.properties").newReader())

        assert metadata["app.name"] == TEST_PROJECT_NAME
        assert metadata["app.version"] == "1.0-SNAPSHOT"
        assert metadata["app.grails.version"] == "1.2.0"
        assert metadata["app.servlet.version"] == "2.4"
        assert metadata["plugins.hibernate"] == "1.2.0"
        assert metadata["plugins.tomcat"] == "1.2.0"
    }

    @Test
    void createPlugin() {
        def testScript = """\
buildscript {
    repositories {
        mavenCentral()
        mavenRepo urls: "http://download.java.net/maven/2/"
        flatDir dirs: "${new File('build/libs').absolutePath}"
    }

    dependencies {
        classpath "org.grails:grails-bootstrap:1.2.0",
                  "org.grails:grails-core:1.2.0",
                  ":grails-build:"
    }
}

apply id: 'grails-x'

projectType = "plugin"

dependencies {
    compile "org.grails:grails-core:1.2.0"
}
"""
        def buildFile = testFile(projectDir, "build.gradle")
        buildFile.write testScript

        def result = usingProjectDir(projectDir).withTasks("init").run()
        if (result.error) {
            println "Error\n-----\n${result.error}"
            assert false
        }

        // Check the common project directories and files.
        verifyProjectStructure()

        // Check for the plugin descriptor.
        def pluginDescriptor = new File(projectDir, "DummyGrailsPlugin.groovy")
        assert pluginDescriptor.exists()

        // Check that the application metadata is initialised correctly.
        def metadata = new Properties()
        metadata.load(new File(projectDir, "application.properties").newReader())

        assert !metadata["app.version"]
        assert !metadata["app.name"]
        assert metadata["app.grails.version"] == "1.2.0"
        assert metadata["app.servlet.version"] == "2.5" // TODO Probably shouldn't be added for plugins.
        assert metadata["plugins.hibernate"] == "1.2.0"
        assert metadata["plugins.tomcat"] == "1.2.0"

        // Check the contents of the plugin descriptor.
        def descriptorContent = pluginDescriptor.text
        assert descriptorContent.find(~/class DummyGrailsPlugin \{/)
        assert descriptorContent.find(~/\s*def version\s*=\s*"0\.1"/)
        assert descriptorContent.find(~/\s*def grailsVersion\s*=\s*"1\.2\.0 > \*"/)
    }

    @Test
    void createPluginWithInvalidName() {
        def testScript = """\
buildscript {
    repositories {
        mavenCentral()
        mavenRepo urls: "http://download.java.net/maven/2/"
        flatDir dirs: "${new File('build/libs').absolutePath}"
    }

    dependencies {
        classpath "org.grails:grails-bootstrap:1.2.0",
                  "org.grails:grails-core:1.2.0",
                  ":grails-build:"
    }
}

apply id: 'grails-x'

projectType = "plugin"

dependencies {
    compile "org.grails:grails-core:1.2.0"
}
"""
        def buildFile = testFile(projectDir, "build.gradle")
        buildFile.write testScript

        def settingsFile = testFile(projectDir, "settings.gradle")
        settingsFile.write "rootProject.name = \"proj1\""

        def result = usingProjectDir(projectDir).withTasks("init").runWithFailure()
        result.assertThatCause startsWith("Specified plugin name [proj1] is invalid.")
    }

    private verifyProjectStructure() {
        assert new File(projectDir, "application.properties").exists()
        assert new File(projectDir, "grails-app/conf/BuildConfig.groovy").exists()
        assert new File(projectDir, "grails-app/controllers").isDirectory()
        assert new File(projectDir, "grails-app/domain").isDirectory()
        assert new File(projectDir, "grails-app/i18n").isDirectory()
        assert new File(projectDir, "grails-app/services").isDirectory()
        assert new File(projectDir, "grails-app/taglib").isDirectory()
        assert new File(projectDir, "grails-app/utils").isDirectory()
        assert new File(projectDir, "grails-app/views/layouts").isDirectory()
        assert new File(projectDir, "lib").isDirectory()
        assert new File(projectDir, "scripts").isDirectory()
        assert new File(projectDir, "src/java").isDirectory()
        assert new File(projectDir, "src/groovy").isDirectory()
        assert new File(projectDir, "test/unit").isDirectory()
        assert new File(projectDir, "test/integration").isDirectory()
        assert new File(projectDir, "web-app/js").isDirectory()
        assert new File(projectDir, "web-app/css").isDirectory()
        assert new File(projectDir, "web-app/images").isDirectory()
        assert new File(projectDir, "web-app/META-INF").isDirectory()
        assert new File(projectDir, "web-app/WEB-INF").isDirectory()

        // There should be no META-INF directory from the JARs.
        assert !new File(projectDir, "META-INF").exists()
    }

    private verifyAppStructure() {
        assert new File(projectDir, "grails-app/conf/BootStrap.groovy").exists()
        assert new File(projectDir, "grails-app/conf/Config.groovy").exists()
        assert new File(projectDir, "grails-app/conf/UrlMappings.groovy").exists()
        assert new File(projectDir, "grails-app/conf/spring/resources.groovy").exists()
        assert new File(projectDir, "grails-app/conf/hibernate").isDirectory()
        assert new File(projectDir, "grails-app/i18n/messages.properties").isDirectory()
        assert new File(projectDir, "grails-app/views/index.gsp").isDirectory()
        assert new File(projectDir, "grails-app/views/layouts/main.gsp").isDirectory()
        assert new File(projectDir, "web-app/images/grails_logo.png").isDirectory()
        assert new File(projectDir, "web-app/js/prototype/prototype.js").isDirectory()
    }

    private verifyPluginStructure() {
        assert new File(projectDir, "scripts/_Install.groovy").exists()
        assert new File(projectDir, "scripts/_Uninstall.groovy").exists()
        assert new File(projectDir, "scripts/_Upgrade.groovy").exists()
    }
}
