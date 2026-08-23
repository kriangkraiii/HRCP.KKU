package com.ecom.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Data model for a user-configured custom evaluation expiry notification timing.
 */
public class CustomExpiryAlert implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private int value;
    private String unit; // "DAYS", "WEEKS", "MONTHS"
    private int days;
    private String label;
    private boolean enabled = true;

    public CustomExpiryAlert() {
    }

    public CustomExpiryAlert(String id, int value, String unit, int days, String label, boolean enabled) {
        this.id = id;
        this.value = value;
        this.unit = unit;
        this.days = days;
        this.label = label;
        this.enabled = enabled;
    }

    public static CustomExpiryAlert create(int value, String unit) {
        int computedDays;
        String unitLabel;
        String normalizedUnit = unit != null ? unit.toUpperCase().trim() : "DAYS";

        switch (normalizedUnit) {
            case "WEEKS" -> {
                computedDays = value * 7;
                unitLabel = "สัปดาห์";
            }
            case "MONTHS" -> {
                computedDays = value * 30;
                unitLabel = "เดือน";
            }
            default -> {
                normalizedUnit = "DAYS";
                computedDays = value;
                unitLabel = "วัน";
            }
        }

        String id = "alert_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
        String label = value + " " + unitLabel + "ก่อนหมด";
        return new CustomExpiryAlert(id, value, normalizedUnit, computedDays, label, true);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomExpiryAlert that = (CustomExpiryAlert) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
