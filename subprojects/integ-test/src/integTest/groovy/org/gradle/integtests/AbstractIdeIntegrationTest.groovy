/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests

abstract class AbstractIdeIntegrationTest extends AbstractIntegrationTest {
    protected void runTask(taskName, settingsScript = "rootProject.name = 'root'", buildScript) {
        def settingsFile = file("settings.gradle")
        settingsFile << settingsScript

        def buildFile = file("build.gradle")
        buildFile << buildScript

        executer.usingSettingsFile(settingsFile).usingBuildScript(buildFile).withTasks(taskName).run()
    }

    protected File getFile(Map options, String filename) {
        def file = options?.project ? file(options.project, filename) : file(filename)
        if (options?.print) { println file.text }
        file
    }

    protected parseFile(Map options, String filename) {
        def file = getFile(options, filename)
        new XmlSlurper().parse(file)
    }

    protected File publishArtifact(dir, group, artifact, dependency = null) {
        def artifactDir = new File("$dir/$group/$artifact/1.0")
        assert artifactDir.mkdirs()

        def pomFile = new File("$artifactDir/$artifact-1.0.pom")
        pomFile << """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$group</groupId>
  <artifactId>$artifact</artifactId>
  <packaging>jar</packaging>
  <version>1.0</version>"""

        if (dependency) {
            pomFile << """
  <dependencies>
    <dependency>
      <groupId>$group</groupId>
      <artifactId>$dependency</artifactId>
      <version>1.0</version>
    </dependency>
  </dependencies>"""
        }

        pomFile << "\n</project>"


        def jarFile = new File("$artifactDir/$artifact-1.0.jar")
        jarFile << "add some content so that file size isn't zero"

        jarFile
    }
}
