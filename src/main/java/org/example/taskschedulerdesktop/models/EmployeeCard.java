package org.example.taskschedulerdesktop.models;

public class EmployeeCard {
    private String name;
    private String position;
    private int numberOfTasks;
    private int completedTasks;
    private String firstTaskDescription;
    private String secondTaskDescription;

    public EmployeeCard(String name, String position, int numberOfTasks, int completedTasks, String firstTaskDescription, String secondTaskDescription) {
        this.name = name;
        this.position = position;
        this.numberOfTasks = numberOfTasks;
        this.completedTasks = completedTasks;
        this.firstTaskDescription = firstTaskDescription;
        this.secondTaskDescription = secondTaskDescription;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
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

    public String getFirstTaskDescription() {
        return firstTaskDescription;
    }

    public void setFirstTaskDescription(String firstTaskDescription) {
        this.firstTaskDescription = firstTaskDescription;
    }

    public String getSecondTaskDescription() {
        return secondTaskDescription;
    }

    public void setSecondTaskDescription(String secondTaskDescription) {
        this.secondTaskDescription = secondTaskDescription;
    }
}
