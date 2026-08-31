package org.example.taskschedulerdesktop.dto;

public class TaskForProjectExtendedPage {
    private String taskName;
    private String type;
    private String deadline;
    private String executor;
    private String status;
    private String priority;
    private boolean synced;

    public TaskForProjectExtendedPage(String taskName, String type, String deadline, String executor, String status, String priority, boolean synced) {
        this.taskName = taskName;
        this.type = type;
        this.deadline = deadline;
        this.executor = executor;
        this.status = status;
        this.priority = priority;
        this.synced = synced;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
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

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}
