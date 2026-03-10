package com.ecom.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form DTO for petition submission.
 */
public class PetitionForm {

    @NotBlank(message = "กรุณากรอกหัวข้อคำร้อง")
    @Size(max = 255, message = "หัวข้อคำร้องต้องไม่เกิน 255 ตัวอักษร")
    private String title;

    @NotBlank(message = "กรุณากรอกรายละเอียดคำร้อง")
    private String description;

    public PetitionForm() {
    }

    public PetitionForm(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
