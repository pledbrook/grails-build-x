/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.build

import org.gradle.CacheUsage
import org.gradle.StartParameter
import org.gradle.integtests.GradleExecuter
import org.gradle.integtests.InProcessGradleExecuter
import org.gradle.util.TestFile

public class AbstractIntegrationTest {
    TestFile testDir = new TestFile(new File("build/tmp/tests"))

//    public TestFile getTestDir() {
//        return testDir.getDir();
//    }
//
//    public TestFile testFile(String name) {
//        return getTestDir().file(name);
//    }
//
    public TestFile testFile(File dir, String name) {
        return new TestFile(dir, name)
    }
//
//    protected File getTestBuildFile(String name) {
//        TestFile sourceFile = resources.getResource("testProjects/" + name);
//        TestFile destFile = testFile(sourceFile.getName());
//        sourceFile.copyTo(destFile);
//        return destFile;
//    }

    private StartParameter startParameter() {
        def gradleHome = new File(System.getenv("GRADLE_HOME"))
        StartParameter parameter = new StartParameter()
        parameter.gradleHomeDir = gradleHome
        parameter.gradleUserHomeDir = getUserHomeDir()
        parameter.searchUpwards = false
        parameter.cacheUsage = CacheUsage.ON
        parameter.currentDir = testDir
        return parameter
    }

    private TestFile getUserHomeDir() {
        def path = System.getProperty("integTest.gradleUserHomeDir", System.getProperty("user.home") + "/.gradle")
        return new TestFile(new File(path));
    }

    protected GradleExecuter inTestDirectory() {
        return inDirectory(getTestDir());
    }

    protected GradleExecuter inDirectory(File directory) {
        return new InProcessGradleExecuter(startParameter()).inDirectory(directory);
    }

    protected GradleExecuter usingBuildFile(File file) {
        StartParameter parameter = startParameter();
        parameter.setBuildFile(file);
        return new InProcessGradleExecuter(parameter);
    }

    protected GradleExecuter usingBuildScript(String script) {
        return new InProcessGradleExecuter(startParameter()).usingBuildScript(script);
    }

    protected GradleExecuter usingProjectDir(File projectDir) {
        StartParameter parameter = startParameter();
        parameter.setProjectDir(projectDir);
        return new InProcessGradleExecuter(parameter);
    }
}
