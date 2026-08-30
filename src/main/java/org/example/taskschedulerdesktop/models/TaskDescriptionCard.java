package org.example.taskschedulerdesktop.models;

public class TaskDescriptionCard {

    private int sequenceNumber;
    private String name;
    private String type;
    private String deadline;
    private String executorInitials;
    private String priority;

    public TaskDescriptionCard(int sequenceNumber, String name, String type, String deadline, String executorInitials, String priority) {
        this.sequenceNumber = sequenceNumber;
        this.name = name;
        this.type = type;
        this.deadline = deadline;
        this.executorInitials = executorInitials;
        this.priority = priority;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDeadline() {
        return deadline;
    }

    public void setDeadline(String deadline) {
        this.deadline = deadline;
    }

    public String getExecutorInitials() {
        return executorInitials;
    }

    public void setExecutorInitials(String executorInitials) {
        this.executorInitials = executorInitials;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}
