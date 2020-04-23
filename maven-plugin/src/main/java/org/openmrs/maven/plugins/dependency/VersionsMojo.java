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
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.xml.XmlStreamReader;

/**
 * The purpose of this Mojo is to analyze any dependencies, and to output a file that contains each
 * of these dependencies, along with information about the current version referred to in the project
 * For snapshots, the remote repository will be queried to retrieve the latest snapshot detail,
 * including timestamp and build number, to add to the version
 */
@Mojo(name = "versions", requiresDependencyResolution = ResolutionScope.TEST)
public class VersionsMojo extends DependencyMojo {

	@Parameter(property = "includeAllArtifacts", defaultValue = "false")
	private boolean includeAllArtifacts;

	@Parameter(property = "outputFile", defaultValue = "${project.build.directory}/versions.yaml")
	private File outputFile;

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
			artifactAndVersion.put(a.getArtifactId(), getVersion(a));
		}
		writeObjectToYamlFile(groupMap, outputFile);
	}

	/**
	 * Returns the version for the given artifact.  In the case of a SNAPSHOT,
	 * this will interrogate the maven metadata in the local and remote repositories, and
	 * return the latest version found based on timestamp
	 */
	public String getVersion(Artifact a) throws MojoExecutionException {
		if (!a.isSnapshot()) {
			getLog().debug(a +  " is not a snapshot, returning version: " + a.getVersion());
			return a.getVersion();
		}
		getLog().debug(a +  " is a snapshot");

		Metadata localMetadata = getLocalMetadata(a);
		String localVersion = getSnapshotVersion(localMetadata, "");
		getLog().debug("Local version: " + localVersion);

		Metadata remoteMetadata = getRemoteMetadata(a);
		String remoteVersion = getSnapshotVersion(remoteMetadata, "");
		getLog().debug("Remote version: " + localVersion);

		if (localVersion.equals(remoteVersion)) {
			getLog().debug("Versions match, returning " + localVersion);
			return localVersion;
		}

		getLog().debug("Versions do not match, checking timestamps");
		String localTimestamp = getSnapshotTimestamp(localMetadata, "");
		String remoteTimestamp = getSnapshotTimestamp(remoteMetadata, "");
		if (localTimestamp.compareTo(remoteTimestamp) > 0) {
			getLog().debug("Local version is newer, returning local version");
			return localVersion;
		}
		getLog().debug("Remote version is newer, returning remote version");
		return remoteVersion;
	}

	/**
	 * @return the timestamp from the snapshot version, or valueIfNotFound if not found
	 */
	public String getSnapshotTimestamp(Metadata metadata, String valueIfNotFound) {
		if (metadata != null && metadata.getVersioning() != null && metadata.getVersioning().getSnapshot() != null) {
			Snapshot snapshot = metadata.getVersioning().getSnapshot();
			try {
				String ts = snapshot.getTimestamp();
				if (StringUtils.isNotEmpty(ts)) {
					return ts;
				}
			}
			catch (Exception e) {
				getLog().warn("Error parsing snapshot timestamp: " + snapshot.getTimestamp(), e);
			}
		}
		return valueIfNotFound;
	}

	/**
	 * @return version-timestamp-build for the latest snapshot found in the metadata, or valueIfNotFound if not found
	 */
	public String getSnapshotVersion(Metadata metadata, String valueIfNotFound) {
		if (metadata != null && metadata.getVersioning() != null && metadata.getVersioning().getSnapshot() != null) {
			Snapshot snapshot = metadata.getVersioning().getSnapshot();
			StringBuilder version = new StringBuilder();
			version.append(metadata.getVersion().replace("-SNAPSHOT", "-"));
			version.append(snapshot.getTimestamp()).append("-").append(snapshot.getBuildNumber());
			return version.toString();
		}
		return valueIfNotFound;
	}

	/**
	 * Retrieve the metadata of the given artifact from the first matching repository
	 */
	public Metadata getLocalMetadata(Artifact artifact) throws MojoExecutionException {
		for (Repository repository : getMavenExecutionEnvironment().getMavenProject().getRepositories()) {
			File artifactDir = artifact.getFile().getParentFile();
			String repoId = repository.getId();
			File metadataFile = new File(artifactDir, "maven-metadata-" + repoId + ".xml");
			if (metadataFile.exists()) {
				MetadataXpp3Reader reader = new MetadataXpp3Reader();
				try (FileInputStream fis = new FileInputStream(metadataFile)) {
					return reader.read(fis);
				}
				catch (Exception e) {
					throw new MojoExecutionException("Unable to read Metadata from file at " + metadataFile);
				}
			}
		}
		return null;
	}

	/**
	 * Retrieve the metadata of the given artifact from the first remote repository that contains a match
	 */
	public Metadata getRemoteMetadata(Artifact artifact) throws MojoExecutionException {
		for (Repository repository : getMavenExecutionEnvironment().getMavenProject().getRepositories()) {
			Path localRepo = new File(getLocalRepository().getBasedir()).toPath();
			Path artifactDir = artifact.getFile().getParentFile().toPath();
			String relativeArtifactDir = localRepo.relativize(artifactDir).toString();
			String remoteUrl = repository.getUrl();
			remoteUrl = remoteUrl.replace("http://", "https://"); // Needed for 301 redirect
			remoteUrl += (remoteUrl.endsWith("/") ? "" : "/") + relativeArtifactDir + "/maven-metadata.xml";
			try {
				MetadataXpp3Reader reader = new MetadataXpp3Reader();
				return reader.read(new XmlStreamReader(new URL(remoteUrl)));
			}
			catch (Exception e) {
				throw new MojoExecutionException("Unable to read Metadata from url at " + remoteUrl);
			}
		}
		return null;
	}
}
