/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.git.internal;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.VersionControlSystem;
import org.gradle.vcs.git.GitVersionControlSpec;

import java.io.File;
import java.io.IOException;

/**
 * A Git {@link VersionControlSystem} implementation.
 */
public class GitVersionControlSystem implements VersionControlSystem {
    private static final Logger LOGGER = Logging.getLogger(GitVersionControlSystem.class);
    @Override
    public void populate(File workingDir, VersionControlSpec spec) {
        if (!(spec instanceof GitVersionControlSpec)) {
            throw new IllegalArgumentException("The GitVersionControlSystem can only handle GitVersionConrolSpec instances.");
        }
        GitVersionControlSpec gitSpec = (GitVersionControlSpec) spec;

        File dbDir = new File(workingDir, ".git");
        if (dbDir.exists() && dbDir.isDirectory()) {
            updateRepo(workingDir, gitSpec);
        } else {
            cloneRepo(workingDir, gitSpec);
        }
    }

    private void cloneRepo(File workingDir, GitVersionControlSpec gitSpec) {
        CloneCommand clone = Git.cloneRepository().setURI(gitSpec.getUrl().toString()).setDirectory(workingDir);
        Git git = null;
        try {
            git = clone.call();
        } catch (GitAPIException e) {
            LOGGER.error("Could not clone: {} to {} because {}", gitSpec.getUrl(), workingDir, e.getStackTrace());
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    private void updateRepo(File workingDir, GitVersionControlSpec gitSpec) {
        Git git = null;
        try {
            git = Git.open(workingDir);
            git.pull().call();
        } catch (IOException e) {
            LOGGER.error("Could not update: {} from {} because {}", workingDir, gitSpec.getUrl(), e.getStackTrace());
        } catch (GitAPIException e) {
            LOGGER.error("Could not update: {} from {} because {}", workingDir, gitSpec.getUrl(), e.getStackTrace());
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }
}
