module org.example.taskschedulerdesktop {

    requires org.slf4j;
    requires javafx.controls;
    requires javafx.fxml;
    requires com.zaxxer.hikari;
    requires java.sql;
    requires com.h2database;


    opens org.example.taskschedulerdesktop to javafx.fxml;
    exports org.example.taskschedulerdesktop;
    exports org.example.taskschedulerdesktop.controllers;
    opens org.example.taskschedulerdesktop.controllers to javafx.fxml;
    exports org.example.taskschedulerdesktop.controllers.projects;
    opens org.example.taskschedulerdesktop.controllers.projects to javafx.fxml;
    exports org.example.taskschedulerdesktop.controllers.tasks;
    opens org.example.taskschedulerdesktop.controllers.tasks to javafx.fxml;
    exports org.example.taskschedulerdesktop.controllers.review;
    opens org.example.taskschedulerdesktop.controllers.review to javafx.fxml;
    exports org.example.taskschedulerdesktop.controllers.sidebar;
    opens org.example.taskschedulerdesktop.controllers.sidebar to javafx.fxml;
    exports org.example.taskschedulerdesktop.controllers.team;
    opens org.example.taskschedulerdesktop.controllers.team to javafx.fxml;
}