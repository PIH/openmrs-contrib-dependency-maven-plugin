/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.maven.plugins.dependency;

import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * This Mojo retrieves the the latest deployed versions file for the current project
 * Typically this would be used in conjunction with the create-versions goal to determine if dependency
 * versions have changed since the last artifact deployment.
 */
@Mojo(name = "retrieve-versions",
		defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
		requiresDependencyResolution = ResolutionScope.TEST)
public class RetrieveVersionsMojo extends DependencyMojo {

	/**
	 * @throws MojoExecutionException if an error occurs
	 */
	public void execute() throws MojoExecutionException {
		try {
			executeMojo(
					plugin("org.apache.maven.plugins", "maven-dependency-plugin", "3.1.2"),
					goal("copy"),
					configuration(
							element("artifact", getVersionsArtifactId()),
							element("useBaseVersion", "true")
					),
					getMavenExecutionEnvironment()
			);
		}
		catch (Exception e) {
			getLog().warn("Unable to retrieve " + getVersionsArtifactId() + " from Maven repository");
		}
		File outputDir = new File(getBuildDir(), "dependency"); // Default output dir for maven-dependency-plugin
		File latestFile = new File(outputDir, getVersionsArtifactFileName());
		if (latestFile.exists()) {
			getLog().info("Retrieved " + latestFile.getName() + " from repository");
			File outputFile = getRetrievedVersionsOutputFile();
			try {
				ensureDirectoryExists(outputFile);
				FileUtils.copyFile(latestFile, outputFile);
			}
			catch (Exception e) {
				throw new MojoExecutionException("Unable to copy latest file to " + outputFile, e);
			}
		}
		else {
			getLog().warn("Unable to locate artifact at " + latestFile);
		}
	}

	/**
	 * @return the artifact id that the dependency plugin uses to retrieve the versions artifact
	 */
	public String getVersionsArtifactId() {
		Artifact a = getMavenExecutionEnvironment().getMavenProject().getArtifact();
		String groupId = a.getGroupId();
		String artifactId = a.getArtifactId();
		String baseVersion = a.getBaseVersion(); // This is the non-timestamped snapshot
		return groupId + ":" + artifactId + ":" + baseVersion + ":" + versionsType + ":" + versionsClassifier;
	}

	/**
	 * @return the fileName created by the dependency plugin to store the downloaded versions artifact
	 * We'll use the same filename for use in the build process, though this is not strictly required.
	 */
	public String getVersionsArtifactFileName() {
		Artifact a = getMavenExecutionEnvironment().getMavenProject().getArtifact();
		String artifactId = a.getArtifactId();
		String baseVersion = a.getBaseVersion(); // This is the non-timestamped snapshot
		return artifactId + "-" + baseVersion + "-" + versionsClassifier + "." + versionsType;
	}
}
