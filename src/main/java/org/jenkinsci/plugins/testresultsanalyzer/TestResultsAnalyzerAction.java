package org.jenkinsci.plugins.testresultsanalyzer;

import hudson.model.*;
import hudson.tasks.test.AggregatedTestResultAction;
import hudson.tasks.test.TabulatedResult;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestResult;
import hudson.util.RunList;
import java.io.*;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.testresultsanalyzer.config.UserConfig;
import org.jenkinsci.plugins.testresultsanalyzer.result.info.ResultInfo;
import org.jenkinsci.plugins.testresultsanalyzer.result.data.ResultData;
import org.jenkinsci.plugins.testresultsanalyzer.result.info.ClassInfo;
import org.jenkinsci.plugins.testresultsanalyzer.result.info.PackageInfo;
import org.jenkinsci.plugins.testresultsanalyzer.result.info.TestCaseInfo;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.bind.JavaScriptMethod;

public class TestResultsAnalyzerAction extends Actionable implements Action {

	@SuppressWarnings("rawtypes")
	Job project;
	private List<Integer> builds = new ArrayList<Integer>();
	private final static Logger LOG = Logger.getLogger(TestResultsAnalyzerAction.class.getName());

	ResultInfo resultInfo;
	private int overrideNoOfFetch = 0;

	private Cache cache;

	public TestResultsAnalyzerAction(@SuppressWarnings("rawtypes") Job project) {
		this.project = project;
		cache = new Cache(project);
	}

	/**
	 * The display name for the action.
	 *
	 * @return the name as String
	 */
	public final String getDisplayName() {
		return this.hasPermission() ? Constants.NAME : null;
	}

	/**
	 * The icon for this action.
	 *
	 * @return the icon file as String
	 */
	public final String getIconFileName() {
		return this.hasPermission() ? Constants.ICONFILENAME : null;
	}

	/**
	 * The url for this action.
	 *
	 * @return the url as String
	 */
	public String getUrlName() {
		return this.hasPermission() ? Constants.URL : null;
	}

	/**
	 * Search url for this action.
	 *
	 * @return the url as String
	 */
	public String getSearchUrl() {
		return this.hasPermission() ? Constants.URL : null;
	}

	/**
	 * Checks if the user has CONFIGURE permission.
	 *
	 * @return true - user has permission, false - no permission.
	 */
	private boolean hasPermission() {
		return project.hasPermission(Item.READ);
	}

	@SuppressWarnings("rawtypes")
	public Job getProject() {
		return this.project;
	}



	@JavaScriptMethod
	public JSONArray getNoOfBuilds(String noOfbuildsNeeded, UserConfig userConfig) {
		JSONArray jsonArray;
		int noOfBuilds = getNoOfBuildRequired(noOfbuildsNeeded);

		jsonArray = getBuildsArray(getBuildList(noOfBuilds, userConfig.getBuildFilter()));

		return jsonArray;
	}

	private JSONArray getBuildsArray(List<Integer> buildList) {
		JSONArray jsonArray = new JSONArray();
		for (Integer build : buildList) {
			jsonArray.add(build);
		}
		return jsonArray;
	}

	private List<Integer> getBuildList(int noOfBuilds, String buildFilter) {
		LOG.info("getBuildList ("+String.valueOf(noOfBuilds)+", "+buildFilter+") build list size: "+String.valueOf(builds.size()));
		List<Integer> buildFilterIds = new ArrayList<Integer>();
		if (!buildFilter.isEmpty()) {
			for (String build: buildFilter.split(",")) {
				LOG.info("filtered part "+build);
				if (build.contains("-")) {
					String[] builds = build.split("-");
					for (int i = Integer.parseInt(builds[0]); i <= Integer.parseInt(builds[1]); i++) {
						buildFilterIds.add(i);
					}
				}
				else if (build.startsWith("!")) {
					LOG.info("remove "+build.substring(1));
					buildFilterIds.remove(Integer.valueOf(build.substring(1)));

				}
				else {
					LOG.info("add "+build.substring(1));
					buildFilterIds.add(Integer.parseInt(build));
				}
			}
		}
		LOG.info("BuildFilter -> "+String.valueOf(buildFilterIds));

		if (noOfBuilds > builds.size()) {
			overrideNoOfFetch = overrideNoOfFetch + noOfBuilds - builds.size();
			LOG.info("ReFetch "+String.valueOf(overrideNoOfFetch)+" builds");
			builds.clear();
			getJsonLoadData();
		}
		else if ((noOfBuilds <= 0) && (buildFilterIds.isEmpty())) {

			return builds;
		}
		else {
			noOfBuilds = builds.size();
		}

		List<Integer> buildList = new ArrayList<Integer>();

		for(int i = 0; (i < noOfBuilds) && (i < builds.size()); i++) {
			Integer build_id = builds.get(i);
			if ((buildFilterIds.contains(build_id)) || (buildFilterIds.isEmpty())) {
				buildList.add(builds.get(i));
			}
		}
		return buildList;
	}

	private int getNoOfBuildRequired(String noOfbuildsNeeded) {

		int noOfBuilds;
		try {
			noOfBuilds = Integer.parseInt(noOfbuildsNeeded);
		}
		catch (NumberFormatException e) {
			noOfBuilds = -1;
		}
		return noOfBuilds;
	}

	public boolean isUpdated() {
		Run lastBuild = project.getLastBuild();
		if (lastBuild == null) {
			return false;
		}
		int latestBuildNumber = lastBuild.getNumber();
		LOG.info(" " + lastBuild.getNumber());
		return !(builds.contains(latestBuildNumber));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public void getJsonLoadData() {
		LOG.info("Get data for report [isUpdated = "+String.valueOf(isUpdated())+"]");
		try {
			if (!cache.isEmpty()) {
			if (!isUpdated() || !cache.isNeedsUpdate()) {
				return;
			}
			}
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}
		resultInfo = new ResultInfo();
		builds = new ArrayList<Integer>();
		RunList<Run> runs = null;
		if (getNoOfRunsToFetch() > 0) {
		    runs = project.getBuilds().limit(getNoOfRunsToFetch());
        } else {
		    runs = project.getBuilds();
		}
		LOG.info("Num of build fetched "+String.valueOf(runs.size()));

		for (Run run : runs) {
			if(run.isBuilding()) {
				continue;
			}
			int buildNumber = run.getNumber();
			builds.add(buildNumber);
			List<AbstractTestResultAction> testActions = run.getActions(AbstractTestResultAction.class);
			for (AbstractTestResultAction testAction : testActions) {
				if (AggregatedTestResultAction.class.isInstance(testAction)) {
					addTestResults(buildNumber, (AggregatedTestResultAction) testAction);
				} else {
					addTestResult(buildNumber, run, testAction, testAction.getResult());
				}
			}
		}
	}

	private void addTestResults(int buildNumber, AggregatedTestResultAction testAction) {
		List<AggregatedTestResultAction.ChildReport> childReports = testAction.getChildReports();
		LOG.warning("Processing build "+String.valueOf(buildNumber)+" reports size: "+String.valueOf(childReports.size()));
		for (AggregatedTestResultAction.ChildReport childReport : childReports) {
			LOG.warning(" child report: "+childReport.run.getDisplayName());
			addTestResult(buildNumber, childReport.run, testAction, childReport.result);
		}
	}

	private void addTestResult(int buildNumber, Run run, AbstractTestResultAction testAction, Object result) {
		if (run == null || result == null) {
			return;
		}
		try {
			TabulatedResult testResult = (TabulatedResult) result;
			Collection<? extends TestResult> packageResults = testResult.getChildren();
			Jenkins jenkins = Jenkins.getInstance();
			String rootUrl = jenkins != null ? jenkins.getRootUrl() : "";
			for (TestResult packageResult : packageResults) { // packageresult
				resultInfo.addPackage(buildNumber, run.getDisplayName().replaceFirst("#(\\d*\\s)", "run:"), (TabulatedResult) packageResult, rootUrl + run.getUrl());
			}
		} catch (ClassCastException e) {
			LOG.info("Got ClassCast exception while converting results to Tabulated Result from action: " + testAction.getClass().getName() + ". Ignoring as we only want test results for processing.");
		}
	}

	private JSONObject generateJsonBuilds(UserConfig userConfig) {
		if (resultInfo == null) {
			return new JSONObject();
		}
		int noOfBuilds = getNoOfBuildRequired(userConfig.getNoOfBuildsNeeded());
		LOG.warning("No of build needed: " + userConfig.getNoOfBuildsNeeded());
		LOG.warning("No of build fetched: " + String.valueOf(noOfBuilds));
		LOG.warning("Build filter: " + userConfig.getBuildFilter());
		List<Integer> buildList = getBuildList(noOfBuilds, userConfig.getBuildFilter());
		JsTreeUtil jsTreeUtils = new JsTreeUtil();
		return jsTreeUtils.getJsTree(buildList, resultInfo, userConfig.isHideConfigMethods());
	}

	private void saveCache(UserConfig userConfig) throws IOException {
		JSONObject builds = generateJsonBuilds(userConfig);
		cache.save(builds);
	}

	@JavaScriptMethod
	public String getCacheString(UserConfig userConfig) throws IOException, ParseException {
		if (cache.isEmpty() || cache.isNeedsUpdate()) {
			LOG.info("Updating cache...");
			saveCache(userConfig);
		}
		LOG.info("Loading cache...");
		return cache.getData();
	}

	@JavaScriptMethod
    public JSONObject updateAndGetBuilds(UserConfig userConfig) throws IOException, ParseException {
		if (cache.isEmpty() || cache.isNeedsUpdate()) {
			return generateJsonBuilds(userConfig);
		} else {
			return JSONObject.fromObject(cache.getData());
		}
    }

    @JavaScriptMethod
	public void clearCache() {
		cache.delete();
	}

	@JavaScriptMethod
    public String getExportCSV(String timeBased, String noOfBuildsNeeded, UserConfig userConfig) {
		boolean isTimeBased = Boolean.parseBoolean(timeBased);
        Map<String, PackageInfo> packageResults = resultInfo.getPackageResults();
		int noOfBuilds = getNoOfBuildRequired(noOfBuildsNeeded);
		List<Integer> buildList = getBuildList(noOfBuilds, userConfig.getBuildFilter());
		LOG.info("GetExport");
		StringBuffer builder = new StringBuffer("");
        for (int i = 0; i < buildList.size(); i++) {
            builder.append(",\"");
            builder.append(Integer.toString(builds.get(i)));
            builder.append("\"");
        }
        String header = "\"Package\",\"Class\",\"Test\"";
        header += builder.toString();

		StringBuilder exportBuilder = new StringBuilder();
        exportBuilder.append(header + System.lineSeparator());
		DecimalFormat decimalFormat = new DecimalFormat("#.###");
		decimalFormat.setRoundingMode(RoundingMode.CEILING);
        for (PackageInfo pInfo : packageResults.values()) {
            String packageName = pInfo.getName();
            //loop the classes
            for (ClassInfo cInfo : pInfo.getClasses().values()) {
                String className = cInfo.getName();
                //loop the tests
                for (TestCaseInfo tInfo : cInfo.getTests().values()) {
                    String testName = tInfo.getName();
                    exportBuilder.append("\""+ packageName + "\",\"" + className + "\",\"" + testName+"\"");
					Map<Integer, ResultData> buildPackageResults = tInfo.getBuildPackageResults();
					for (int i = 0; i < buildList.size(); i++) {
						Integer buildNumber = buildList.get(i);
						String data = getCustomStatus("NA");
						if(buildPackageResults.containsKey(buildNumber)) {
							ResultData buildResult = buildPackageResults.get(buildNumber);
							if(!isTimeBased) {
								data = getCustomStatus(buildResult.getStatus());
							} else {
								data = decimalFormat.format(buildResult.getTotalTimeTaken());
							}
						}
						exportBuilder.append(",\"" + data + "\"");
					}
                    exportBuilder.append(System.lineSeparator());
                }
            }
        }
        return exportBuilder.toString();
    }

	private String getCustomStatus(String status) {
		ResultStatus resultStatus = null;
		try {
			resultStatus = ResultStatus.valueOf(status);
		} catch (IllegalArgumentException e) {
		    resultStatus = null;
		}
		if (resultStatus == null)
			return status;
		switch (resultStatus) {
			case PASSED:
				return getPassedRepresentation();
			case FAILED:
				return getFailedRepresentation();
			case SKIPPED:
				return getSkippedRepresentation();
			case NA:
				return getNaRepresentation();
		}
		return status;
	}

	public String getNoOfBuilds() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getNoOfBuilds();
	}

	public int getNoOfRunsToFetch() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getNoOfRunsToFetch() + overrideNoOfFetch;
	}

	public boolean getShowAllBuilds() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getShowAllBuilds();
	}

	public boolean getShowLineGraph() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getShowLineGraph();
	}

	public boolean getShowBarGraph() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getShowBarGraph();
	}

	public boolean getShowPieGraph() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getShowPieGraph();
	}

	public boolean getShowBuildTime() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getShowBuildTime();
	}

	public boolean getHideConfigurationMethods() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getHideConfigurationMethods();
	}

	public String getChartDataType() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getChartDataType();
	}

	public String getRunTimeLowThreshold() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getRunTimeLowThreshold();
	}

	public String getRunTimeHighThreshold() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getRunTimeHighThreshold();
	}

	public boolean isUseCustomStatusNames() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.isUseCustomStatusNames();
	}

	public String getPassedRepresentation() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getPassedRepresentation();
	}

	public String getFailedRepresentation() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getFailedRepresentation();
	}

	public String getSkippedRepresentation() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getSkippedRepresentation();
	}

	public String getNaRepresentation() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getNaRepresentation();
	}

    public String getPassedColor() {
        return TestResultsAnalyzerExtension.DESCRIPTOR.getPassedColor();
    }
}
