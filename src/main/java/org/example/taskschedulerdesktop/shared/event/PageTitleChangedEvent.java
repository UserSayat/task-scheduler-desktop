package org.example.taskschedulerdesktop.shared.event;

public class PageTitleChangedEvent {

    private final String pageTitle;

    public PageTitleChangedEvent(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public String getPageTitle() {
        return pageTitle;
    }
}
