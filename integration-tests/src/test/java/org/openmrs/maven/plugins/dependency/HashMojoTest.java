package org.openmrs.maven.plugins.dependency;

import java.io.File;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.junit.Assert;
import org.junit.Test;

public class HashMojoTest {

	@Test
	public void testDependenciesAreHashed() throws Exception {
		File projectDir = ResourceExtractor.simpleExtractResources(getClass(), "/hash-mojo-test");
		Verifier verifier = new Verifier(projectDir.getAbsolutePath());
		verifier.setCliOptions(Arrays.asList("-N"));
		verifier.executeGoal("clean");
		verifier.executeGoal("generate-resources");

		String expectedOutput = FileUtils.readFileToString(new File(projectDir, "expected-output.yml")).trim();
		String actualOutput = FileUtils.readFileToString(new File(projectDir, "target/hashes.yml")).trim();
		Assert.assertEquals(expectedOutput, actualOutput);
	}
}
