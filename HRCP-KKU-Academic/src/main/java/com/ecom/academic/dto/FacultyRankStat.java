package com.ecom.academic.dto;

import java.io.Serializable;

/**
 * Summary statistics of faculty members by current academic rank.
 */
public record FacultyRankStat(
        long totalFaculty,
        long professorCount,
        long associateProfessorCount,
        long assistantProfessorCount,
        long lecturerCount,
        double promotedPercentage
) implements Serializable {

    public long totalPromoted() {
        return professorCount + associateProfessorCount + assistantProfessorCount;
    }
}
