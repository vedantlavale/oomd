package com.vsms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddTaskForm {

    @NotBlank(message = "Describe the task.")
    @Size(max = 200, message = "A task can have at most 200 characters.")
    private String description;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
