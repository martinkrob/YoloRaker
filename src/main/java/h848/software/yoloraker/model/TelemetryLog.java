package h848.software.yoloraker.model;

import java.sql.Timestamp;

public class TelemetryLog {

    private Long id;
    private String printerId;
    private Timestamp timestamp;
    private double extruderTemp;
    private double bedTemp;
    private double printProgress;
    private float confSpaghetti;
    private float confStringing;
    private float confZits;

    public TelemetryLog() {
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

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public double getExtruderTemp() {
        return extruderTemp;
    }

    public void setExtruderTemp(double extruderTemp) {
        this.extruderTemp = extruderTemp;
    }

    public double getBedTemp() {
        return bedTemp;
    }

    public void setBedTemp(double bedTemp) {
        this.bedTemp = bedTemp;
    }

    public double getPrintProgress() {
        return printProgress;
    }

    public void setPrintProgress(double printProgress) {
        this.printProgress = printProgress;
    }

    public float getConfSpaghetti() {
        return confSpaghetti;
    }

    public void setConfSpaghetti(float confSpaghetti) {
        this.confSpaghetti = confSpaghetti;
    }

    public float getConfStringing() {
        return confStringing;
    }

    public void setConfStringing(float confStringing) {
        this.confStringing = confStringing;
    }

    public float getConfZits() {
        return confZits;
    }

    public void setConfZits(float confZits) {
        this.confZits = confZits;
    }
}
