package com.vsms.dto;

import jakarta.validation.constraints.Size;

/** What the mechanic found and did; staff and the assigned mechanic can both edit it. */
public class JobNotesForm {

    @Size(max = 2000, message = "The diagnosis can have at most 2000 characters.")
    private String diagnosis;

    @Size(max = 2000, message = "The repair notes can have at most 2000 characters.")
    private String repairNotes;

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getRepairNotes() { return repairNotes; }
    public void setRepairNotes(String repairNotes) { this.repairNotes = repairNotes; }
}
