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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.utils.xml.XmlStreamReader;

/**
 * The purpose of this Mojo is to analyze any Snapshot artifact dependencies, and to
 * output a file that contains each of these dependencies, along with information about which
 * specific snapshot is current in the repository (timestamp and build number)
 */
@Mojo(name = "list-snapshots", requiresDependencyResolution = ResolutionScope.TEST)
public class ListSnapshotsMojo extends DependencyMojo {

	@Parameter(property = "outputFile", defaultValue = "${project.build.directory}/snapshots.json")
	private File outputFile;

	/**
	 * @throws MojoExecutionException if an error occurs
	 */
	public void execute() throws MojoExecutionException {
		Map<String, List<Map<String, String>>> snapshots = new TreeMap<>();
		Set<Artifact> artifacts = getMavenExecutionEnvironment().getMavenProject().getArtifacts();
		for (Artifact a : artifacts) {
			if (a.isSnapshot()) {
				String artifact = a.toString();
				getLog().debug("Checking snapshot " + artifact);

				// For each repository defined, compare local vs. remote metadata if defined
				for (Repository repository : getMavenExecutionEnvironment().getMavenProject().getRepositories()) {
					Metadata metadata = getLocalMetadata(a, repository);
					if (metadata != null && metadata.getVersioning() != null) {
						Snapshot snapshot = metadata.getVersioning().getSnapshot();
						if (snapshot != null) {
							Map<String, String> s = new LinkedHashMap<>();
							s.put("repository", repository.getId());
							s.put("timestamp", snapshot.getTimestamp());
							s.put("buildNumber", "" + snapshot.getBuildNumber());
							List<Map<String, String>> l = snapshots.get(artifact);
							if (l == null) {
								l = new ArrayList<>();
								snapshots.put(artifact, l);
							}
							l.add(s);
						}
					}
				}
			}
		}
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
			writer.writeValue(outputFile, snapshots);
		}
		catch (Exception e) {
			throw new MojoExecutionException("Error writing snapshots to file", e);
		}
	}

	/**
	 * Retrieve the metadata of the given artifact from the given local repository
	 */
	public Metadata getLocalMetadata(Artifact artifact, Repository repository) throws MojoExecutionException {
		Metadata ret = null;
		File artifactDir = artifact.getFile().getParentFile();
		String repoId = repository.getId();
		File metadataFile = new File(artifactDir, "maven-metadata-" + repoId + ".xml");
		if (metadataFile.exists()) {
			MetadataXpp3Reader reader = new MetadataXpp3Reader();
			try (FileInputStream fis = new FileInputStream(metadataFile)) {
				ret = reader.read(fis);
			}
			catch (Exception e) {
				throw new MojoExecutionException("Unable to read Metadata from file at " + metadataFile);
			}
		}
		return ret;
	}

	/**
	 * Retrieve the metadata of the given artifact from the given remote repository
	 */
	public Metadata getRemoteMetadata(Artifact artifact, Repository repository) throws MojoExecutionException {
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
}
