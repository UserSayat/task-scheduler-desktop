package org.example.taskschedulerdesktop.models;

public class Task {

    private int sequenceNumber;
    private String name;
    private String projectName;
    private String executor;
    private String priority;
    private String status;
    private String term;

    public Task(int sequenceNumber, String name, String projectName, String executor, String priority, String status, String term) {
        this.sequenceNumber = sequenceNumber;
        this.name = name;
        this.projectName = projectName;
        this.executor = executor;
        this.priority = priority;
        this.status = status;
        this.term = term;
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

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }
}
