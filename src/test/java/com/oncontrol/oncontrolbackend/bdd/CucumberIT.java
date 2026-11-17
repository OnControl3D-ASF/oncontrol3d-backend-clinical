package com.oncontrol.oncontrolbackend.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Runs the Gherkin acceptance tests (src/test/resources/features) with failsafe.
 * Reports: target/cucumber-reports/cucumber.html and cucumber.json.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.oncontrol.oncontrolbackend.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,"
        + "html:target/cucumber-reports/cucumber.html,"
        + "json:target/cucumber-reports/cucumber.json")
public class CucumberIT {
}
