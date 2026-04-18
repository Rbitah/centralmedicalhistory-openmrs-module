package org.openmrs.module.nationalhistory.api;

import java.util.List;

import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.nationalhistory.api.model.HistoryRecord;

public interface NationalHistoryService extends OpenmrsService {

    List<HistoryRecord> getHistoryRecordsForPatient(String patientUuid) throws APIException;

    void clearCacheForPatient(String patientUuid);
}
