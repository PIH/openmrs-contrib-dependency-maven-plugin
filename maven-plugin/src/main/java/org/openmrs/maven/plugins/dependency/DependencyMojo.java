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

import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.twdata.maven.mojoexecutor.MojoExecutor;

/**
 * Base Mojo superclass to be used within the dependency plugin
 */
public abstract class DependencyMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true)
	MavenProject mavenProject;

	@Parameter(defaultValue = "${session}", readonly = true)
	MavenSession mavenSession;

	@Component
	BuildPluginManager pluginManager;

	/**
	 * Convenience method to get the execution environment for invoking other Maven plugins
	 */
	protected MojoExecutor.ExecutionEnvironment getMavenExecutionEnvironment() {
		return executionEnvironment(mavenProject, mavenSession, pluginManager);
	}

	/**
	 * Convenience method to get the source directory for this project
	 */
	protected File getBaseDir() {
		return mavenProject.getBasedir();
	}

	/**
	 * Convenience method to get the build directory used by all plugins.  This is typically "/target"
	 */
	protected File getBuildDir() {
		return new File(mavenProject.getBuild().getDirectory());
	}

	/**
	 * Convenience method to get access to the Local Repository
	 */
	protected ArtifactRepository getLocalRepository() {
		return mavenSession.getLocalRepository();
	}

	/**
	 * Convenience method to enable writing an object to an output file as yaml
	 */
	protected void writeObjectToYamlFile(Object o, File outputFile) throws MojoExecutionException {
		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
			writer.writeValue(outputFile, o);
		}
		catch (Exception e) {
			throw new MojoExecutionException("Error writing to Yaml output file", e);
		}
	}
}
