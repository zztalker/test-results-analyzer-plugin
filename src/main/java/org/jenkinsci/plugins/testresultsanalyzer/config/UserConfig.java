package org.jenkinsci.plugins.testresultsanalyzer.config;

import jenkins.model.RunAction2;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by vmenon on 3/17/2016.
 */
public class UserConfig {
    private boolean hideConfigMethods = false;

    private String noOfBuildsNeeded;
    private String buildFilter = "";

    @DataBoundConstructor
    public UserConfig(String noOfBuildsNeeded, boolean hideConfigMethods, String buildFilter) {
        this.noOfBuildsNeeded = noOfBuildsNeeded;
        this.hideConfigMethods = hideConfigMethods;
        this.buildFilter = buildFilter;
    }

    public boolean isHideConfigMethods() {
        return hideConfigMethods;
    }

    public void setHideConfigMethods(boolean hideConfigMethods) {
        this.hideConfigMethods = hideConfigMethods;
    }

    public String getNoOfBuildsNeeded() {
        return noOfBuildsNeeded;
    }

    public void setNoOfBuildsNeeded(String noOfBuildsNeeded) {
        this.noOfBuildsNeeded = noOfBuildsNeeded;
    }

    public void setBuildFiler(String buildFilter) {
        this.buildFilter = buildFilter;
    }

    public String getBuildFilter() {
        return buildFilter;
    }
}
