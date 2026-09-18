package org.example.taskschedulerdesktop.models;

public class Project {
    private Long id;
    private String name;
    private String supervisor;
    private Integer numberOfTasks;
    private Integer completedTasks;
    private Integer remainingTasks;
    private Integer percentOfCompletion;
    private boolean synced;

    public Project(Long id, String name, String supervisor, Integer numberOfTasks, Integer completedTasks, Integer remainingTasks, Integer percentOfCompletion, boolean synced) {
        this.id = id;
        this.name = name;
        this.supervisor = supervisor;
        this.numberOfTasks = numberOfTasks;
        this.completedTasks = completedTasks;
        this.remainingTasks = remainingTasks;
        this.percentOfCompletion = percentOfCompletion;
        this.synced = synced;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(String supervisor) {
        this.supervisor = supervisor;
    }

    public Integer getNumberOfTasks() {
        return numberOfTasks;
    }

    public void setNumberOfTasks(Integer numberOfTasks) {
        this.numberOfTasks = numberOfTasks;
    }

    public Integer getCompletedTasks() {
        return completedTasks;
    }

    public void setCompletedTasks(Integer completedTasks) {
        this.completedTasks = completedTasks;
    }

    public Integer getRemainingTasks() {
        return remainingTasks;
    }

    public void setRemainingTasks(Integer remainingTasks) {
        this.remainingTasks = remainingTasks;
    }

    public Integer getPercentOfCompletion() {
        return percentOfCompletion;
    }

    public void setPercentOfCompletion(Integer percentOfCompletion) {
        this.percentOfCompletion = percentOfCompletion;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}
