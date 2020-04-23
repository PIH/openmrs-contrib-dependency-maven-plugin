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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * The purpose of this Mojo is to attach a versions file as a maven artifact, to be installed into a Maven
 * repository for storage and future reference, with classifier="versions" and type="yml"
 */
@Mojo(name = "attach-versions", defaultPhase = LifecyclePhase.PACKAGE)
public class AttachVersionsMojo extends DependencyMojo {

	/**
	 * @throws MojoExecutionException if an error occurs
	 */
	public void execute() throws MojoExecutionException {
		executeMojo(
				plugin("org.codehaus.mojo", "build-helper-maven-plugin", "3.1.0"),
				goal("attach-artifact"),
				configuration(
						element("artifacts",
								element("artifact",
										element("file", getVersionsOutputFile().getAbsolutePath()),
										element("classifier", versionsClassifier),
										element("type", versionsType)
								)
						)
				),
				getMavenExecutionEnvironment()
		);
	}
}
