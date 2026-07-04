package h848.software.yoloraker.model;

import java.sql.Timestamp;

public class PrintJob {

    private Long id;
    private String printerId;
    private String filename;
    private Timestamp startTime;
    private Timestamp endTime;
    private String status; // "printing", "completed", "cancelled", "error", "paused_by_ai"
    private double durationSeconds;
    private double extrudedFilament;

    public PrintJob() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPrinterId() {
        return printerId;
    }

    public void setPrinterId(String printerId) {
        this.printerId = printerId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Timestamp getStartTime() {
        return startTime;
    }

    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
    }

    public Timestamp getEndTime() {
        return endTime;
    }

    public void setEndTime(Timestamp endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public double getExtrudedFilament() {
        return extrudedFilament;
    }

    public void setExtrudedFilament(double extrudedFilament) {
        this.extrudedFilament = extrudedFilament;
    }
}
