package org.openmrs.module.nationalhistory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.util.OpenmrsUtil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

public class NationalHistoryActivator extends BaseModuleActivator {

    private final Log log = LogFactory.getLog(this.getClass());
    private static final String DIRECTIVE_NAME = "nationalHistoryDashboard";
    private static final String DASHBOARD_SECTION_KEY = "\"nationalHistory\"";
    private static final Pattern EXISTING_DIRECTIVE_PATTERN = Pattern.compile(
            "angular\\.module\\('bahmni\\.common\\.displaycontrol\\.custom'\\)\\s*"
                    + "\\.directive\\('nationalHistoryDashboard'[\\s\\S]*?\\}\\]\\);",
            Pattern.MULTILINE);

    @Override
    public void started() {
        log.info("Started National History module");
        try {
            applyBahmniAutoConfiguration();
        } catch (Exception e) {
            log.warn("Unable to auto-configure Bahmni dashboard for National History module: " + e.getMessage(), e);
        }
    }

    @Override
    public void stopped() {
        log.info("Stopped National History module");
    }

    private void applyBahmniAutoConfiguration() throws IOException {
        Path appDataRoot = Paths.get(OpenmrsUtil.getApplicationDataDirectory());
        Path configRoot = appDataRoot.resolve(Paths.get("configuration", "openmrs", "apps"));
        if (!Files.exists(configRoot)) {
            Path bahmniConfigRoot = appDataRoot.resolve(Paths.get("bahmni_config", "openmrs", "apps"));
            if (Files.exists(bahmniConfigRoot)) {
                configRoot = bahmniConfigRoot;
            } else {
                log.info("Bahmni config path not found. Skipping auto-configuration: " + configRoot
                        + " and " + bahmniConfigRoot);
                return;
            }
        }

        Path customControlJs = configRoot.resolve(Paths.get("customDisplayControl", "js", "customControl.js"));
        Path dashboardJson = configRoot.resolve(Paths.get("clinical", "dashboard.json"));

        if (!Files.exists(customControlJs) || !Files.exists(dashboardJson)) {
            log.info("Bahmni files not found. Skipping auto-configuration.");
            return;
        }

        copyResourceIfPresent("bahmni/customDisplayControl/js/nationalHistoryDashboard.js",
                configRoot.resolve(Paths.get("customDisplayControl", "js", "nationalHistoryDashboard.js")));
        copyResourceIfPresent("bahmni/customDisplayControl/views/nationalHistoryDashboard.html",
                configRoot.resolve(Paths.get("customDisplayControl", "views", "nationalHistoryDashboard.html")));

        ensureDirectiveLoaded(customControlJs);
        ensureDashboardSection(dashboardJson);
        log.info("Bahmni auto-configuration for National History completed.");
    }

    private void copyResourceIfPresent(String resourcePath, Path targetPath) throws IOException {
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (resourceStream == null) {
            log.warn("Module resource not found: " + resourcePath);
            return;
        }

        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (InputStream in = new BufferedInputStream(resourceStream)) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureDirectiveLoaded(Path customControlJs) throws IOException {
        String customControlContent = new String(Files.readAllBytes(customControlJs), StandardCharsets.UTF_8);

        Path directiveFile = customControlJs.getParent().resolve("nationalHistoryDashboard.js");
        if (!Files.exists(directiveFile)) {
            log.warn("Directive file missing, cannot append to customControl.js: " + directiveFile);
            return;
        }

        String directiveContent = new String(Files.readAllBytes(directiveFile), StandardCharsets.UTF_8);
        String withoutOldDirective = EXISTING_DIRECTIVE_PATTERN.matcher(customControlContent).replaceAll("").trim();
        String updatedContent = withoutOldDirective + System.lineSeparator() + System.lineSeparator() + directiveContent;
        Files.write(customControlJs, updatedContent.getBytes(StandardCharsets.UTF_8));
    }

    private void ensureDashboardSection(Path dashboardJson) throws IOException {
        String content = new String(Files.readAllBytes(dashboardJson), StandardCharsets.UTF_8);
        if (content.contains(DASHBOARD_SECTION_KEY)) {
            return;
        }

        String insertionPoint = "\"patientDocument\":{";
        int index = content.indexOf(insertionPoint);
        if (index < 0) {
            insertionPoint = "\"patientDocument\": {";
            index = content.indexOf(insertionPoint);
        }

        if (index < 0) {
            log.warn("Could not find patientDocument section in dashboard.json; skipping section injection.");
            return;
        }

        String section = "            \"nationalHistory\": {\n"
                + "                \"type\": \"custom\",\n"
                + "                \"displayOrder\": 9,\n"
                + "                \"config\": {\n"
                + "                    \"title\": \"National Medical History\",\n"
                + "                    \"template\": \"<national-history-dashboard section=\\\"section\\\" patient=\\\"patient\\\"></national-history-dashboard>\"\n"
                + "                }\n"
                + "            },\n";

        String updated = content.substring(0, index) + section + content.substring(index);
        Files.write(dashboardJson, updated.getBytes(StandardCharsets.UTF_8));
    }
}
