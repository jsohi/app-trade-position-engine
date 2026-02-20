package com.bofa.equity.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.bofa.equity.position")
public class CucumberRunner {
    // JUnit Platform Suite runner for Cucumber scenarios.
    // Discovers feature files from classpath:features/
    // Step definitions are in com.bofa.equity.position (for package-private access to PositionAggregator).
}
