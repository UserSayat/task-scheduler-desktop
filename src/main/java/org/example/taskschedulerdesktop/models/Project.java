package org.example.taskschedulerdesktop.models;

public class Project {
    private Long id;
    private String name;
    private String supervisor;
    private boolean synced;

    public Project(Long id, String name, String supervisor, boolean synced) {
        this.id = id;
        this.name = name;
        this.supervisor = supervisor;
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

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}
