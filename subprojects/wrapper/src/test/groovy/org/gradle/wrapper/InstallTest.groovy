/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.wrapper

import org.gradle.util.TemporaryFolder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import static org.junit.Assert.assertEquals

/**
 * @author Hans Dockter
 */
class InstallTest {
    File testDir
    Install install
    IDownload downloadMock
    PathAssembler pathAssemblerMock;
    boolean downloadCalled
    File zip
    File distributionDir
    File zipStore
    File gradleScript
    File gradleHomeDir
    File zipDestination
    WrapperConfiguration configuration = new WrapperConfiguration()
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before public void setUp() {
        downloadCalled = false
        testDir = tmpDir.dir
        configuration.zipBase = PathAssembler.PROJECT_STRING
        configuration.zipPath = 'someZipPath'
        configuration.distributionBase = PathAssembler.GRADLE_USER_HOME_STRING
        configuration.distributionPath = 'someDistPath'
        configuration.distribution = new URI('http://server/gradle-0.9.zip')
        configuration.alwaysDownload = false
        configuration.alwaysUnpack = false
        distributionDir = new File(testDir, 'someDistPath')
        gradleHomeDir = new File(distributionDir, 'gradle-0.9')
        zipStore = new File(testDir, 'zips');
        zipDestination = new File(zipStore, 'gradle-0.9.zip')
        install = new Install(createDownloadMock(), createPathAssemblerMock())
    }

    IDownload createDownloadMock() {
        [download: {URI url, File destination ->
            assertEquals(configuration.distribution, url)
            assertEquals(zipDestination.getAbsolutePath() + '.part', destination.getAbsolutePath())
            zip = createTestZip()
            downloadCalled = true
        }] as IDownload
    }

    PathAssembler createPathAssemblerMock() {
        [gradleHome: {String distBase, String distPath, URI distUrl ->
            assertEquals(configuration.distributionBase, distBase)
            assertEquals(configuration.distributionPath, distPath)
            assertEquals(configuration.distribution, distUrl)
            gradleHomeDir},
         distZip: { String zipBase, String zipPath, URI distUrl ->
            assertEquals(configuration.zipBase, zipBase)
            assertEquals(configuration.zipPath, zipPath)
             assertEquals(configuration.distribution, distUrl)
            zipDestination
        }] as PathAssembler
    }

    File createTestZip() {
        File explodedZipDir = new File(testDir, 'explodedZip')
        File binDir = new File(explodedZipDir, 'bin')
        binDir.mkdirs()
        gradleScript = new File(binDir, 'gradle')
        gradleScript.write('something')
        zipStore.mkdirs()
        AntBuilder antBuilder = new AntBuilder()
        antBuilder.zip(destfile: zipDestination.absolutePath + '.part') {
            zipfileset(dir: explodedZipDir, prefix: 'gradle-0.9')
        }
        (zipDestination.absolutePath + '.part') as File
    }

    @Test public void testCreateDist() {
        assertEquals(gradleHomeDir, install.createDist(configuration))
        assert downloadCalled
        assert distributionDir.isDirectory()
        assert zipDestination.exists()
        assert gradleScript.exists()
//        assert new File(gradleHomeDir, "bin/gradle").canExecute()
    }

    @Test public void testCreateDistWithExistingRoot() {
        distributionDir.mkdirs()
        install.createDist(configuration)
        assert downloadCalled
        assert gradleHomeDir.isDirectory()
        assert gradleScript.exists()
    }

    @Test public void testCreateDistWithExistingDist() {
        gradleHomeDir.mkdirs()
        long lastModified = gradleHomeDir.lastModified()
        install.createDist(configuration)
        assert !downloadCalled
        assert lastModified == gradleHomeDir.lastModified()
    }

    @Test public void testCreateDistWithExistingDistAndZipAndAlwaysUnpackTrue() {
        createTestZip().renameTo(zipDestination)
        gradleHomeDir.mkdirs()
        File testFile = new File(gradleHomeDir, 'testfile')
        configuration.alwaysUnpack = true
        install.createDist(configuration)
        assert distributionDir.isDirectory()
        assert gradleScript.exists()
        assert !testFile.exists()
        assert !downloadCalled
    }

    @Test public void testCreateDistWithExistingZipAndDistAndAlwaysDownloadTrue() {
        createTestZip().renameTo(zipDestination)
        distributionDir.mkdirs()
        File testFile = new File(gradleHomeDir, 'testfile')
        configuration.alwaysDownload = true
        install.createDist(configuration)
        assert gradleHomeDir.isDirectory()
        assert gradleScript.exists()
        assert !testFile.exists()
        assert downloadCalled
    }
}
