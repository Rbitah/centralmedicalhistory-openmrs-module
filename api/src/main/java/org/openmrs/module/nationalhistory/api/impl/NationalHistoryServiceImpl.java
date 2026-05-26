package org.openmrs.module.nationalhistory.api.impl;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Type;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.nationalhistory.NationalHistoryConstants;
import org.openmrs.module.nationalhistory.api.NationalHistoryService;
import org.openmrs.module.nationalhistory.api.model.HistoryRecord;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;

public class NationalHistoryServiceImpl extends BaseOpenmrsService implements NationalHistoryService {

    private static final Log log = LogFactory.getLog(NationalHistoryServiceImpl.class);
    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    private static final long CACHE_TTL_MILLIS = 5L * 60L * 1000L;
    private static final String DEFAULT_TEXT = "N/A";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private final Map<String, CacheEntry> patientCache = new ConcurrentHashMap<String, CacheEntry>();

    @Override
    public List<HistoryRecord> getHistoryRecordsForPatient(String patientUuid) throws APIException {
        if (StringUtils.isBlank(patientUuid)) {
            log.warn("Patient UUID is blank. Returning empty national history.");
            return Collections.emptyList();
        }

        CacheEntry cached = patientCache.get(patientUuid);
        if (cached != null && !cached.isExpired()) {
            return cached.copyRecords();
        }

        Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
        if (patient == null) {
            return cacheAndReturnEmpty(patientUuid,
                "Patient not found for uuid " + patientUuid + ". Returning empty national history.");
        }

        String patientIdentifier = resolvePatientIdentifier(patient);
        if (StringUtils.isBlank(patientIdentifier)) {
            return cacheAndReturnEmpty(patientUuid,
                "No usable patient identifier found for patient uuid " + patientUuid + ". Returning empty national history.");
        }

        String baseUrl = StringUtils.trimToEmpty(
            Context.getAdministrationService().getGlobalProperty(NationalHistoryConstants.GP_FHIR_BASE_URL));
        if (StringUtils.isBlank(baseUrl)) {
            return cacheAndReturnEmpty(patientUuid,
                "FHIR base URL is not configured. Returning empty national history for patient " + patientUuid + ".");
        }

        String token = StringUtils.trimToEmpty(
            Context.getAdministrationService().getGlobalProperty(NationalHistoryConstants.GP_FHIR_TOKEN));

        try {
            IGenericClient client = buildClient(baseUrl, token);

            List<HistoryRecord> records = new ArrayList<HistoryRecord>();
            records.addAll(safelyFetchConditions(client, patientIdentifier));
            records.addAll(safelyFetchEncounters(client, patientIdentifier));
            records.addAll(safelyFetchAllergies(client, patientIdentifier));
            records.addAll(safelyFetchAppointments(client, patientIdentifier));
            records.addAll(safelyFetchAttachments(client, patientIdentifier));
            sortByDateDesc(records);

            patientCache.put(patientUuid, new CacheEntry(records));
            return new ArrayList<HistoryRecord>(records);
        }
        catch (Exception ex) {
            log.error("Unable to fetch National Medical History from external FHIR server for patient " + patientUuid
                    + ". Returning empty list instead.", ex);
            return cacheAndReturnEmpty(patientUuid, null);
        }
    }

    @Override
    public void clearCacheForPatient(String patientUuid) {
        if (StringUtils.isNotBlank(patientUuid)) {
            patientCache.remove(patientUuid);
        }
    }

    private List<HistoryRecord> cacheAndReturnEmpty(String patientUuid, String logMessage) {
        if (StringUtils.isNotBlank(logMessage)) {
            log.warn(logMessage);
        }
        List<HistoryRecord> emptyRecords = Collections.emptyList();
        patientCache.put(patientUuid, new CacheEntry(emptyRecords));
        return new ArrayList<HistoryRecord>(emptyRecords);
    }

    private IGenericClient buildClient(String baseUrl, String token) {
        IGenericClient client = FHIR_CONTEXT.newRestfulGenericClient(baseUrl);
        if (StringUtils.isNotBlank(token)) {
            client.registerInterceptor(new BearerTokenAuthInterceptor(token));
        }
        return client;
    }

    private List<HistoryRecord> fetchConditions(IGenericClient client, String patientIdentifier) {
        String url = "Condition?patient.identifier=" + urlEncode(patientIdentifier);
        List<IBaseResource> resources = fetchBundleResources(client, url);

        List<HistoryRecord> records = new ArrayList<HistoryRecord>();
        for (IBaseResource resource : resources) {
            if (!(resource instanceof Condition)) {
                continue;
            }
            Condition condition = (Condition) resource;
            String summary = conditionSummary(condition.getCode());
            String date = formatDate(resolveConditionDate(condition));
            String facility = firstNonBlank(
                referenceDisplay(condition.getEncounter()),
                referenceDisplay(condition.getRecorder()),
                referenceDisplay(condition.getAsserter()),
                DEFAULT_TEXT);

            records.add(new HistoryRecord(resolveConditionType(condition), summary, date, facility));
        }

        return records;
    }

    private List<HistoryRecord> fetchEncounters(IGenericClient client, String patientIdentifier) {
        String url = "Encounter?patient.identifier=" + urlEncode(patientIdentifier);
        List<IBaseResource> resources = fetchBundleResources(client, url);

        List<HistoryRecord> records = new ArrayList<HistoryRecord>();
        for (IBaseResource resource : resources) {
            if (!(resource instanceof Encounter)) {
                continue;
            }
            Encounter encounter = (Encounter) resource;

            String summary = firstNonBlank(
                codeableConceptSummary(encounter.hasType() ? encounter.getTypeFirstRep() : null),
                encounter.hasClass_() ? encounter.getClass_().getDisplay() : null,
                encounter.getStatus() != null ? encounter.getStatus().toCode() : null,
                DEFAULT_TEXT);

            String date = formatDate(resolveEncounterDate(encounter));
            String facility = firstNonBlank(
                referenceDisplay(encounter.getServiceProvider()),
                encounter.hasLocation() ? referenceDisplay(encounter.getLocationFirstRep().getLocation()) : null,
                DEFAULT_TEXT);

            records.add(new HistoryRecord("Recent Visit", summary, date, facility));
        }

        return records;
    }

    private List<HistoryRecord> fetchAllergies(IGenericClient client, String patientIdentifier) {
        String url = "AllergyIntolerance?patient.identifier=" + urlEncode(patientIdentifier);
        List<IBaseResource> resources = fetchBundleResources(client, url);

        List<HistoryRecord> records = new ArrayList<HistoryRecord>();
        for (IBaseResource resource : resources) {
            if (!(resource instanceof AllergyIntolerance)) {
                continue;
            }
            AllergyIntolerance allergy = (AllergyIntolerance) resource;

            String summary = firstNonBlank(
                codeableConceptSummary(allergy.getCode()),
                allergy.hasCriticality() ? allergy.getCriticality().toCode() : null,
                allergy.hasClinicalStatus() ? codeableConceptSummary(allergy.getClinicalStatus()) : null,
                DEFAULT_TEXT);
            String date = formatDate(allergy.getRecordedDate());
            String facility = firstNonBlank(
                referenceDisplay(allergy.getRecorder()),
                referenceDisplay(allergy.getAsserter()),
                DEFAULT_TEXT);

            records.add(new HistoryRecord("Allergy", summary, date, facility));
        }

        return records;
    }

    private List<HistoryRecord> fetchAppointments(IGenericClient client, String patientIdentifier) {
        String url = "Appointment?patient.identifier=" + urlEncode(patientIdentifier);
        List<IBaseResource> resources = fetchBundleResources(client, url);

        List<HistoryRecord> records = new ArrayList<HistoryRecord>();
        for (IBaseResource resource : resources) {
            if (!(resource instanceof Appointment)) {
                continue;
            }
            Appointment appointment = (Appointment) resource;

            String summary = firstNonBlank(
                appointment.hasDescription() ? appointment.getDescription() : null,
                appointment.hasServiceCategory() ? codeableConceptSummary(appointment.getServiceCategoryFirstRep()) : null,
                appointment.hasServiceType() ? codeableConceptSummary(appointment.getServiceTypeFirstRep()) : null,
                appointment.hasAppointmentType() ? codeableConceptSummary(appointment.getAppointmentType()) : null,
                appointment.hasStatus() ? appointment.getStatus().toCode() : null,
                DEFAULT_TEXT);
            String date = formatDate(appointment.hasStart() ? appointment.getStart() : null);
            String facility = firstNonBlank(
                appointment.hasParticipant()
                    ? referenceDisplay(appointment.getParticipantFirstRep().getActor())
                    : null,
                DEFAULT_TEXT);

            records.add(new HistoryRecord("Appointment", summary, date, facility));
        }

        return records;
    }

    private List<HistoryRecord> fetchAttachments(IGenericClient client, String patientIdentifier) {
        String url = "DocumentReference?patient.identifier=" + urlEncode(patientIdentifier);
        List<IBaseResource> resources = fetchBundleResources(client, url);

        List<HistoryRecord> records = new ArrayList<HistoryRecord>();
        for (IBaseResource resource : resources) {
            if (!(resource instanceof DocumentReference)) {
                continue;
            }
            DocumentReference documentReference = (DocumentReference) resource;

            String summary = firstNonBlank(
                documentReference.hasDescription() ? documentReference.getDescription() : null,
                documentReference.hasType() ? codeableConceptSummary(documentReference.getType()) : null,
                documentReference.hasCategory() ? codeableConceptSummary(documentReference.getCategoryFirstRep()) : null,
                DEFAULT_TEXT);
            String date = formatDate(documentReference.hasDate() ? documentReference.getDate() : null);
            String facility = firstNonBlank(
                referenceDisplay(documentReference.getCustodian()),
                documentReference.hasAuthor() ? referenceDisplay(documentReference.getAuthorFirstRep()) : null,
                DEFAULT_TEXT);

            records.add(new HistoryRecord("Attachment", summary, date, facility));
        }

        return records;
    }

    private List<HistoryRecord> safelyFetchConditions(IGenericClient client, String patientIdentifier) {
        try {
            return fetchConditions(client, patientIdentifier);
        }
        catch (Exception ex) {
            log.warn("Failed fetching Condition resources from MPI", ex);
            return Collections.emptyList();
        }
    }

    private List<HistoryRecord> safelyFetchEncounters(IGenericClient client, String patientIdentifier) {
        try {
            return fetchEncounters(client, patientIdentifier);
        }
        catch (Exception ex) {
            log.warn("Failed fetching Encounter resources from MPI", ex);
            return Collections.emptyList();
        }
    }

    private List<HistoryRecord> safelyFetchAllergies(IGenericClient client, String patientIdentifier) {
        try {
            return fetchAllergies(client, patientIdentifier);
        }
        catch (Exception ex) {
            log.warn("Failed fetching AllergyIntolerance resources from MPI", ex);
            return Collections.emptyList();
        }
    }

    private List<HistoryRecord> safelyFetchAppointments(IGenericClient client, String patientIdentifier) {
        try {
            return fetchAppointments(client, patientIdentifier);
        }
        catch (Exception ex) {
            log.warn("Failed fetching Appointment resources from MPI", ex);
            return Collections.emptyList();
        }
    }

    private List<HistoryRecord> safelyFetchAttachments(IGenericClient client, String patientIdentifier) {
        try {
            return fetchAttachments(client, patientIdentifier);
        }
        catch (Exception ex) {
            log.warn("Failed fetching DocumentReference resources from MPI", ex);
            return Collections.emptyList();
        }
    }

    private List<IBaseResource> fetchBundleResources(IGenericClient client, String searchUrl) {
        List<IBaseResource> resources = new ArrayList<IBaseResource>();

        Bundle bundle = client.search().byUrl(searchUrl).returnBundle(Bundle.class).execute();
        while (bundle != null) {
            if (bundle.hasEntry()) {
                for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                    if (entry != null && entry.hasResource()) {
                        resources.add(entry.getResource());
                    }
                }
            }

            if (bundle.getLink(Bundle.LINK_NEXT) != null && StringUtils.isNotBlank(bundle.getLink(Bundle.LINK_NEXT).getUrl())) {
                bundle = client.loadPage().next(bundle).execute();
            } else {
                break;
            }
        }

        return resources;
    }

    private String resolvePatientIdentifier(Patient patient) {
        PatientIdentifier preferred = patient.getPatientIdentifier();
        if (preferred != null
                && !preferred.getVoided()
                && StringUtils.isNotBlank(preferred.getIdentifier())) {
            return preferred.getIdentifier();
        }

        for (PatientIdentifier identifier : patient.getIdentifiers()) {
            if (!identifier.getVoided() && StringUtils.isNotBlank(identifier.getIdentifier())) {
                return identifier.getIdentifier();
            }
        }

        return null;
    }

    private Date resolveConditionDate(Condition condition) {
        Type onset = condition.getOnset();
        if (onset instanceof DateTimeType) {
            return ((DateTimeType) onset).getValue();
        }
        if (onset instanceof Period) {
            return ((Period) onset).getStart();
        }
        if (condition.getRecordedDate() != null) {
            return condition.getRecordedDate();
        }
        return null;
    }

    private Date resolveEncounterDate(Encounter encounter) {
        if (encounter.getPeriod() != null && encounter.getPeriod().getStart() != null) {
            return encounter.getPeriod().getStart();
        }
        return null;
    }

    private String conditionSummary(CodeableConcept code) {
        return firstNonBlank(
            codeableConceptSummary(code),
            DEFAULT_TEXT);
    }

    private String resolveConditionType(Condition condition) {
        if (condition != null && condition.hasCategory()) {
            for (CodeableConcept category : condition.getCategory()) {
                if (category != null && category.hasCoding()) {
                    for (Coding coding : category.getCoding()) {
                        String code = coding != null ? firstNonBlank(coding.getCode(), coding.getDisplay()) : null;
                        if (StringUtils.isNotBlank(code) && code.toLowerCase().contains("diagnos")) {
                            return "Diagnosis";
                        }
                    }
                }
                String text = category != null ? category.getText() : null;
                if (StringUtils.isNotBlank(text) && text.toLowerCase().contains("diagnos")) {
                    return "Diagnosis";
                }
            }
        }
        return "Condition";
    }

    private String codeableConceptSummary(CodeableConcept concept) {
        if (concept == null) {
            return null;
        }

        if (StringUtils.isNotBlank(concept.getText())) {
            return concept.getText();
        }

        if (concept.hasCoding()) {
            Coding coding = concept.getCodingFirstRep();
            return firstNonBlank(coding.getDisplay(), coding.getCode());
        }

        return null;
    }

    private String referenceDisplay(Reference reference) {
        if (reference == null) {
            return null;
        }

        return firstNonBlank(reference.getDisplay(), reference.getReference());
    }

    private String formatDate(Date date) {
        if (date == null) {
            return DEFAULT_TEXT;
        }
        return new SimpleDateFormat(DATE_PATTERN).format(date);
    }

    private void sortByDateDesc(List<HistoryRecord> records) {
        Collections.sort(records, new Comparator<HistoryRecord>() {

            @Override
            public int compare(HistoryRecord left, HistoryRecord right) {
                String leftDate = left != null ? left.getDate() : null;
                String rightDate = right != null ? right.getDate() : null;
                if (leftDate == null && rightDate == null) {
                    return 0;
                }
                if (leftDate == null) {
                    return 1;
                }
                if (rightDate == null) {
                    return -1;
                }
                return rightDate.compareTo(leftDate);
            }
        });
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new APIException("Failed to encode URL parameter", e);
        }
    }

    private static final class CacheEntry {

        private final long createdAt;
        private final List<HistoryRecord> records;

        private CacheEntry(List<HistoryRecord> records) {
            this.createdAt = System.currentTimeMillis();
            this.records = new ArrayList<HistoryRecord>(records);
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MILLIS;
        }

        private List<HistoryRecord> copyRecords() {
            return new ArrayList<HistoryRecord>(records);
        }
    }
}
