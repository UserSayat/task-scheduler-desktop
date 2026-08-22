package org.example.taskschedulerdesktop.models;

public class ProjectCard {

    private String projectName;

    private String projectSupervisor;

    private int percentOfCompletion;

    private int numberOfTasks;

    private int completedTasks;

    private int remainingTasks;

    private String firstTaskDescription;

    private String firstTaskDeadline;

    private String secondTaskDescription;

    private String secondTaskDeadline;

    private String thirdTaskDescription;

    private String thirdTaskDeadline;

    public ProjectCard(String projectNameLabel, String projectSupervisor, int percentOfCompletion, int numberOfTasks, int completedTasks, int remainingTasks, String firstTaskDescription, String firstTaskDeadline, String secondTaskDescription, String secondTaskDeadline, String thirdTaskDescription, String thirdTaskDeadline) {
        this.projectName = projectNameLabel;
        this.projectSupervisor = projectSupervisor;
        this.percentOfCompletion = percentOfCompletion;
        this.numberOfTasks = numberOfTasks;
        this.completedTasks = completedTasks;
        this.remainingTasks = remainingTasks;
        this.firstTaskDescription = firstTaskDescription;
        this.firstTaskDeadline = firstTaskDeadline;
        this.secondTaskDescription = secondTaskDescription;
        this.secondTaskDeadline = secondTaskDeadline;
        this.thirdTaskDescription = thirdTaskDescription;
        this.thirdTaskDeadline = thirdTaskDeadline;
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

    public String getFirstTaskDescription() {
        return firstTaskDescription;
    }

    public void setFirstTaskDescription(String firstTaskDescription) {
        this.firstTaskDescription = firstTaskDescription;
    }

    public String getFirstTaskDeadline() {
        return firstTaskDeadline;
    }

    public void setFirstTaskDeadline(String firstTaskDeadline) {
        this.firstTaskDeadline = firstTaskDeadline;
    }

    public String getSecondTaskDescription() {
        return secondTaskDescription;
    }

    public void setSecondTaskDescription(String secondTaskDescription) {
        this.secondTaskDescription = secondTaskDescription;
    }

    public String getSecondTaskDeadline() {
        return secondTaskDeadline;
    }

    public void setSecondTaskDeadline(String secondTaskDeadline) {
        this.secondTaskDeadline = secondTaskDeadline;
    }

    public String getThirdTaskDescription() {
        return thirdTaskDescription;
    }

    public void setThirdTaskDescription(String thirdTaskDescription) {
        this.thirdTaskDescription = thirdTaskDescription;
    }

    public String getThirdTaskDeadline() {
        return thirdTaskDeadline;
    }

    public void setThirdTaskDeadline(String thirdTaskDeadline) {
        this.thirdTaskDeadline = thirdTaskDeadline;
    }
}
