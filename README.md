openmrs-dependency-maven-plugin
==============================

The goal of this project is to provide tools for analyzing the dependencies in a Maven project.
The primary use case this plugin was written to solve is to enable tracking the specific dependency versions that
Maven uses during the build and test phase of a project, to bundle this version information into the built artifact,
and to compare the version information in the latest deployed artifact to the most recent versions.
This allows CI processes to use this tooling to detect if any upstream dependency (most likely the specific 
SNAPSHOT) has changed, and if so to re-initiate the build.

## Usage

Add the following to the build plugins section of your pom, including the goals of interest.
Each of these goals are explained below.

```xml
<plugin>
    <groupId>org.openmrs.maven.plugins</groupId>
    <artifactId>openmrs-dependency-maven-plugin</artifactId>
    <version>x.y.z</version>
    <executions>
        <execution>
            <goals>
                <goal>retrieve-versions</goal>
                <goal>create-versions</goal>
                <goal>compare-versions</goal>
                <goal>attach-versions</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Goals

The retrieve-versions, create-versions, copmare-versions, and attach-versions goals are all designed to be able to 
work together using the default configurations, and share a common set of configuration properties with the same defaults.

```yaml
outputDir: "target/openmrs-dependency"
versionsClassifier: "versions"
versionsType: "yml"
```
It is unlikely that these will need to be altered, but one can do so if they wish.  These will be referred to in the
below documentation with a $ in front of them.

### create-versions

The create-versions goal runs by default during the generate-resources phase, 
and is responsible for analyzing the dependencies in the current project and outputting a file 
that contains this dependency data.  By default, this will output all dependencies explicitly listed in the 
dependencies section of the POM.  It will _not_ output any dependencies simply listed in dependencyManagement but not
actually included as a dependency, nor will it include plugin dependencies or transitive dependencies.

If one wishes to output all artifacts rather than just dependencies (which includes plugins and transitive dependencies),
one can do so by specifying a configuration property of includeAllArtifacts=true.

Once executed, this goal will create a file at ```$outputDir/$versionsClassifier.$versionsType``` with each dependency 
listed in alphabetical order, first by groupId, then by artifactId.  An example looks like the following.  Note 
that SNAPSHOT dependencies use specific timestamp/build versions.  This allows this artifact to provide an
unambiguous record of exactly what artifacts were used within the build process.

```yaml
---
org.openmrs.api:
  openmrs-api: "1.9.9"
org.openmrs.module:
  calculation-api: "1.0"
  logic-api: "0.5.2"
  orderextension-api: "2.0-20200415.124018-137"
  reporting-api: "0.10.0"
  rowperpatientreports-api: "1.5.6-20200415.124450-3"
  serialization.xstream-api: "0.2.7"
org.openmrs.test:
  openmrs-test: "1.9.9"
org.openmrs.web:
  openmrs-web: "1.9.9"
```
### attach-versions

The attach-versions goal runs by default during the package phase, to add the versions file located at 
```$outputDir/$versionsClassifier.$versionsType``` as a Maven artifact with classifier=$versionsClassifier and type=$versionsType.
Typically this runs in conjunction with the create-versions goal to attach the versions file created.

Running a ```mvn clean install``` on the project with the create-versions goal, followed by the attach-versions goal,
with default configuration, will result in an artifact installed to the local Maven repository with the contents
from the create-versions goal at ```$repository/$groupPath/$artifactId/$baseVersion/$artifactId-$version-versions.yml```

Running a ```maven deploy``` on the project will result in this versions file being deployed to the remote Maven repository.

### retrieve-versions

The retrieve-versions goal runs by default during the generate-resources phase,
and downloads the latest installed/deployed versions file that was either installed or deployed previously.

In a typical workflow, this would be executed first, in order to ensure that the previous versions build artifact is
extracted prior to a new versions build artifact being created.  If no prior versions file is found that matches the 
current project, then nothing will happen.  If a prior versions file is found, this will be made available at
```$outputDir/$versionsClassifier-retrieved.$versionsType```

### compare-versions

The compare-versions goal runs by default during the generate-resources phase, and is responsible for comparing two
different versions files and indicating whether they are the same differ.  Typically, this runs last and compares the file
downloaded by the retrieve-versions goal with the file generated by the create-versions goal to assess whether versions
have changed since the last build.

The following additional configuration options are available, along with their default values:

```yaml
compareFrom: "$outputDir/$versionsClassifier-retrieved.$versionsType"
compareTo: "$outputDir/$versionsClassifier.$versionsType"
missingStatus: "MISSING"
differStatus: "DIFFER"
matchStatus: "MATCH"
```

Up to two output files are produced by this goal:

* *versions-diff-status.txt* - Contains the appropriate status value as configured
  * $missingStatus - Indicates that one or both of the comparison files was not found, and no comparison could occur
  * $differStatus - Indicates that both files exist and differ in content
  * $matchStatus - Indicates that both files exist and match in content
* *versions-diff.yml* - Created only for $differStatus, contains the details a what has changed between the two files

## Example Usages

In a CI process, use this process to detect if any dependencies are changed, and if so, trigger a new build.
The below snippet is an example workflow from Github Actions:

```yaml
# For every commit pushed to the master branch, or any change detected to dependencies in Maven,
# this will compile, package, test, verify, and deploy the master branch.

name: Deploy Snapshots

on:
  push:
    branches: ['master']   # Always deploy on commits pushed to master

  schedule:
    - cron: '0 */1 * * *'  # Also check hourly for any dependency updates (adjust schedule as needed)

  repository_dispatch:
    types: ['deploy-snapshots']  # Also add a hook that enables kicking this off manually if desired

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:

      # Check out the code
      - uses: actions/checkout@v2

        # Enable caching of Maven dependencies to speed up job execution.  See https://github.com/actions/cache
      - uses: actions/cache@v1
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: |
            ${{ runner.os }}-maven-

        # Set up Java 1.8 with Maven including a .m2/settings.xml file.  See https://github.com/actions/setup-java
      - name: Set up JDK 1.8 and Maven settings file
        uses: actions/setup-java@v1
        with:
          java-version: 1.8
          server-id: openmrs-repo-modules-pih-snapshots  # Adjust this to match the distributionManagement of your pom
          server-username: BINTRAY_USERNAME
          server-password: BINTRAY_PASSWORD

        # Execute the Maven command to report on current dependencies and most recently deployed dependencies
        # This is configured in the pom of the project, using the openmrs-dependencies plugin
      - name: Generate Dependency Reports
        run: mvn generate-resources -U --file pom.xml  # Run with -U to ensure it always updates from remote repositories

        # Upload version artifacts to store with build results.  This is a helpful build artifact to preserve.
      - name: Upload dependency reports
        uses: actions/upload-artifact@v1
        with:
          name: openmrs-dependency
          path: target/openmrs-dependency

        # Set diff status to an environment variable
      - name: Set environment
        run: echo "::set-env name=DIFF_STATUS::`cat target/openmrs-dependency/versions-diff-status.txt`"

        # If this workflow was initiated by a push or if version changes are detected, initiate redeploy
      - name: Maven Deploy
        run: mvn -B deploy --file pom.xml
        env:
          BINTRAY_USERNAME: pih
          BINTRAY_PASSWORD: ${{ secrets.BINTRAY_PASSWORD }}  # You need to ensure this secret is added to your gihhub repo
        if: github.event_name == 'push' || env.DIFF_STATUS == 'DIFFER'





```
