module org.example.taskschedulerdesktop {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.taskschedulerdesktop to javafx.fxml;
    exports org.example.taskschedulerdesktop;
    exports org.example.taskschedulerdesktop.controllers;
    opens org.example.taskschedulerdesktop.controllers to javafx.fxml;
}