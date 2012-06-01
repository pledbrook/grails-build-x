package grails.build

import org.testng.annotations.Test
import org.testng.annotations.BeforeClass
import org.testng.annotations.AfterClass

/**
 *
 */
class CreateToPackageTest extends AbstractIntegrationTest {
    private static final String TEST_PROJECT_NAME = "proj1"

    File projectDir

    @BeforeClass
    void createTempDir() {
        projectDir = new File("build/test-projects/${TEST_PROJECT_NAME}")
        projectDir.mkdirs()
    }

    @AfterClass
    void deleteTempDir() {
        projectDir.deleteDir()
    }

    @Test
    void createApplication() {
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

        assert new File(projectDir, "application.properties").exists()
        assert new File(projectDir, "grails-app/conf/BuildConfig.groovy").exists()
        assert new File(projectDir, "grails-app/conf/Config.groovy").exists()
        assert new File(projectDir, "grails-app/conf/UrlMappings.groovy").exists()
        assert new File(projectDir, "grails-app/conf/spring/resources.groovy").exists()
        assert new File(projectDir, "web-app/WEB-INF").exists()

        // There should be no META-INF directory from the JARs.
        assert !new File(projectDir, "META-INF").exists()
    }
}
