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

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.diff.JsonDiff;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * This Mojo compares two version files and outputs two files.
 * The first file contains details on what versions differ.
 * The second file contains a status on the comparison
 */
@Mojo(name = "compare-versions",
		defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
		requiresDependencyResolution = ResolutionScope.TEST)
public class CompareVersionsMojo extends DependencyMojo {

	@Parameter(property = "compareFrom")
	File compareFrom;

	@Parameter(property = "compareTo")
	File compareTo;

	@Parameter(property = "missingStatus", defaultValue = "MISSING")
	String missingStatus;

	@Parameter(property = "differStatus", defaultValue = "DIFFER")
	String differStatus;

	@Parameter(property = "matchStatus", defaultValue = "MATCH")
	String matchStatus;

	/**
	 * @throws MojoExecutionException if an error occurs
	 */
	public void execute() throws MojoExecutionException {
		if (compareFrom == null) {
			compareFrom = getRetrievedVersionsOutputFile();
			getLog().info("Using default compareFrom of " + compareFrom);
		}
		if (compareTo == null) {
			compareTo = getVersionsOutputFile();
			getLog().info("Using default compareTo of " + compareTo);
		}
		File diffStatusFile = new File(outputDir, "versions-diff-status.txt");
		File diffContentsFile = new File(outputDir, "versions-diff.yml");
		if (!compareFrom.exists() || !compareTo.exists()) {
			writeStringToFile(missingStatus, diffStatusFile);
		} else {
			try {
				JsonNode fromJson = readObjectFromYamlFile(compareFrom);
				JsonNode toNode = readObjectFromYamlFile(compareTo);
				if (fromJson.equals(toNode)) {
					writeStringToFile(matchStatus, diffStatusFile);
				}
				else {
					writeStringToFile(differStatus, diffStatusFile);
					JsonNode patchNode = JsonDiff.asJson(fromJson, toNode);
					writeStringToFile(patchNode.toPrettyString(), diffContentsFile);
				}
			}
			catch (Exception e) {
				throw new MojoExecutionException("An error occurred comparing versions from yaml", e);
			}
		}
	}
}
