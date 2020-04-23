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

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * The purpose of this Mojo is to analyze any dependencies, and to output a file that contains each
 * of these dependencies, along with information about the current version referred to in the project
 * Snapshots will be referred to by their actual build and timestamp to remove ambiguity and allow version comparision
 */
@Mojo(name = "create-versions",
		defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
		requiresDependencyResolution = ResolutionScope.TEST)
public class CreateVersionsMojo extends DependencyMojo {

	@Parameter(property = "includeAllArtifacts", defaultValue = "false")
	boolean includeAllArtifacts;

	/**
	 * @throws MojoExecutionException if an error occurs
	 */
	public void execute() throws MojoExecutionException {
		Map<String, Map<String, String>> groupMap = new TreeMap<>();
		MavenProject mp = getMavenExecutionEnvironment().getMavenProject();
		Set<Artifact> artifacts = (includeAllArtifacts ? mp.getArtifacts() : mp.getDependencyArtifacts());
		for (Artifact a : artifacts) {
			Map<String, String> artifactAndVersion = groupMap.get(a.getGroupId());
			if (artifactAndVersion == null) {
				artifactAndVersion = new TreeMap<>();
				groupMap.put(a.getGroupId(), artifactAndVersion);
			}
			artifactAndVersion.put(a.getArtifactId(), a.getVersion());
		}
		writeObjectToYamlFile(groupMap, getVersionsOutputFile());
	}
}
