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

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * The purpose of this Mojo is to support the creation of a file that contains all dependencies referenced
 * by the project, along with the digest hash of each of the actual files retrieved for each.
 * This only looks at actual dependencies, not dependency management or properties.
 * The goal is to allow comparison across executions as to which artifacts have changed.
 * The primary intended use case is to use this information in a CI pipeline to determine if any snapshot dependencies
 * for a project have updated, and to use this information to determine if a new build and deployment should execute.
 * The exclusions property allows for certain artifacts to be excluded from this file.
 * The typical use case would be to skip the -api project dependency it the -omod configuration
 */
@Mojo(name = "hash", requiresDependencyResolution = ResolutionScope.TEST)
public class HashMojo extends DependencyMojo {

	public static final String SHA1_HEX = "sha1Hex";
	public static final String MD5_HEX = "md5Hex";

	@Parameter(property = "algorithm", defaultValue = SHA1_HEX)
	private String algorithm;

	@Parameter(property = "exclusions")
	private List<Dependency> exclusions;

	@Parameter(property = "outputFile", defaultValue = "${project.build.directory}/hashes.yml")
	private File outputFile;

	/**
	 * @throws MojoExecutionException if an error occurs
	 */
	public void execute() throws MojoExecutionException {
		Map<String, String> artifactHashes = new TreeMap<>();
		Set<Artifact> artifacts = getMavenExecutionEnvironment().getMavenProject().getDependencyArtifacts();
		for (Artifact a : artifacts) {
			if (!shouldExclude(a)) {
				artifactHashes.put(a.toString(), hashFile(a.getFile()));
			}
		}
		try {
			if (!getBuildDir().exists()) {
				getBuildDir().mkdirs();
			}
			writeObjectToYamlFile(artifactHashes, outputFile);
		}
		catch (Exception e) {
			throw new MojoExecutionException("Error writing snapshots to file", e);
		}
	}

	/**
	 * Exclusions should be in the form groupId:artifactId:version
	 */
	private boolean shouldExclude(Artifact a) {
		if (exclusions != null) {
			for (Dependency d : exclusions) {
				if (d.getGroupId() == null || d.getGroupId().equalsIgnoreCase(a.getGroupId())) {
					if (d.getArtifactId() == null || d.getArtifactId().equalsIgnoreCase(a.getArtifactId())) {
						if (d.getVersion() == null || d.getVersion().equalsIgnoreCase(a.getVersion())) {
							if (d.getClassifier() == null || d.getClassifier().equalsIgnoreCase(a.getClassifier())) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	/**
	 * Hash the given file, using the specified algorithm property
	 */
	private String hashFile(File file) throws MojoExecutionException {
		try (FileInputStream fis = FileUtils.openInputStream(file)) {
			if (SHA1_HEX.equalsIgnoreCase(algorithm)) {
				return DigestUtils.sha1Hex(fis);
			}
			else if (MD5_HEX.equalsIgnoreCase(algorithm)) {
				return DigestUtils.md5Hex(fis);
			}
			else {
				throw new MojoExecutionException("Please specify either " + MD5_HEX + " or " + SHA1_HEX + " as algorithm");
			}
		}
		catch (Exception e) {
			throw new MojoExecutionException("Unable to generate MD5 for file: " + file, e);
		}
	}
}
