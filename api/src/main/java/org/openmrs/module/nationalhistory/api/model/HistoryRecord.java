package org.openmrs.module.nationalhistory.api.model;

public class HistoryRecord {

    private String type;
    private String summary;
    private String date;
    private String facility;

    public HistoryRecord() {
    }

    public HistoryRecord(String type, String summary, String date, String facility) {
        this.type = type;
        this.summary = summary;
        this.date = date;
        this.facility = facility;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }
}
