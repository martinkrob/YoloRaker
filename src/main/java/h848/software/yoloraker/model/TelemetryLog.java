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

    // --- Sensor fusion record (shadow mode analysis) ---
    // The raw confidences above are kept untouched; everything below is what fusion derived from
    // them, so the two can be compared offline without having to re-run anything.
    private Float refSpaghetti;
    private Float refStringing;
    private Float refZits;
    private Float suppression;
    private String fusionRules;
    /**
     * Only ever populated on write. {@code getTelemetryLogs} deliberately does not select it -
     * it is a few hundred bytes of JSON per row kept for offline analysis, and the charts never
     * read it - so it is also kept out of the serialised payload rather than shipped as nulls.
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String telemetryWindow;
    private Integer anchorsSpaghetti;
    private Integer anchorsStringing;
    private Integer anchorsZits;

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

    public Float getRefSpaghetti() {
        return refSpaghetti;
    }

    public void setRefSpaghetti(Float refSpaghetti) {
        this.refSpaghetti = refSpaghetti;
    }

    public Float getRefStringing() {
        return refStringing;
    }

    public void setRefStringing(Float refStringing) {
        this.refStringing = refStringing;
    }

    public Float getRefZits() {
        return refZits;
    }

    public void setRefZits(Float refZits) {
        this.refZits = refZits;
    }

    public Float getSuppression() {
        return suppression;
    }

    public void setSuppression(Float suppression) {
        this.suppression = suppression;
    }

    public String getFusionRules() {
        return fusionRules;
    }

    public void setFusionRules(String fusionRules) {
        this.fusionRules = fusionRules;
    }

    public String getTelemetryWindow() {
        return telemetryWindow;
    }

    public void setTelemetryWindow(String telemetryWindow) {
        this.telemetryWindow = telemetryWindow;
    }

    public Integer getAnchorsSpaghetti() {
        return anchorsSpaghetti;
    }

    public void setAnchorsSpaghetti(Integer anchorsSpaghetti) {
        this.anchorsSpaghetti = anchorsSpaghetti;
    }

    public Integer getAnchorsStringing() {
        return anchorsStringing;
    }

    public void setAnchorsStringing(Integer anchorsStringing) {
        this.anchorsStringing = anchorsStringing;
    }

    public Integer getAnchorsZits() {
        return anchorsZits;
    }

    public void setAnchorsZits(Integer anchorsZits) {
        this.anchorsZits = anchorsZits;
    }
}
