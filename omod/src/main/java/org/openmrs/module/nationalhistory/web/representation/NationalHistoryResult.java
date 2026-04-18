package org.openmrs.module.nationalhistory.web.representation;

import java.util.ArrayList;
import java.util.List;

import org.openmrs.module.nationalhistory.api.model.HistoryRecord;

public class NationalHistoryResult {

    private List<HistoryRecord> records = new ArrayList<HistoryRecord>();

    public NationalHistoryResult() {
    }

    public NationalHistoryResult(List<HistoryRecord> records) {
        this.records = records;
    }

    public List<HistoryRecord> getRecords() {
        return records;
    }

    public void setRecords(List<HistoryRecord> records) {
        this.records = records;
    }
}
