package org.example.taskschedulerdesktop.models;

public class ProjectCard {

    private String projectName;

    private String projectSupervisor;

    private int percentOfCompletion;

    private int numberOfTasks;

    private int completedTasks;

    private int remainingTasks;

//    private String firstTaskDescription;
//
//    private String firstTaskDeadline;
//
//    private String secondTaskDescription;
//
//    private String secondTaskDeadline;
//
//    private String thirdTaskDescription;
//
//    private String thirdTaskDeadline;

    public ProjectCard(String projectNameLabel, String projectSupervisor, int percentOfCompletion, int numberOfTasks, int completedTasks, int remainingTasks) {
        this.projectName = projectNameLabel;
        this.projectSupervisor = projectSupervisor;
        this.percentOfCompletion = percentOfCompletion;
        this.numberOfTasks = numberOfTasks;
        this.completedTasks = completedTasks;
        this.remainingTasks = remainingTasks;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectNameLabel) {
        this.projectName = projectNameLabel;
    }

    public String getProjectSupervisor() {
        return projectSupervisor;
    }

    public void setProjectSupervisor(String projectSupervisor) {
        this.projectSupervisor = projectSupervisor;
    }

    public int getPercentOfCompletion() {
        return percentOfCompletion;
    }

    public void setPercentOfCompletion(int percentOfCompletion) {
        this.percentOfCompletion = percentOfCompletion;
    }

    public int getNumberOfTasks() {
        return numberOfTasks;
    }

    public void setNumberOfTasks(int numberOfTasks) {
        this.numberOfTasks = numberOfTasks;
    }

    public int getCompletedTasks() {
        return completedTasks;
    }

    public void setCompletedTasks(int completedTasks) {
        this.completedTasks = completedTasks;
    }

    public int getRemainingTasks() {
        return remainingTasks;
    }

    public void setRemainingTasks(int remainingTasks) {
        this.remainingTasks = remainingTasks;
    }
}
