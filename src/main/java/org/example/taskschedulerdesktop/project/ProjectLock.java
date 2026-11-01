package org.example.taskschedulerdesktop.project;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.SVGPath;

/**
 * Значок замка у названия закрытого проекта: в боковом меню, на обзоре и в карточке проекта.
 */
public final class ProjectLock {

    /** Замок 10×12. */
    private static final String LOCK = "M2 5V3.5a3 3 0 0 1 6 0V5h.5A1.5 1.5 0 0 1 10 6.5v4A1.5 1.5 0 0 1 8.5 12h-7A1.5 1.5 0 0 1 "
            + "0 10.5v-4A1.5 1.5 0 0 1 1.5 5H2zm1.5 0h3V3.5a1.5 1.5 0 0 0-3 0V5z";

    private ProjectLock() {}

    /** Показать замок справа от названия, если проект закрытый; у открытого — убрать. */
    public static void apply(Label label, boolean isPrivate) {
        if (!isPrivate) {
            label.setGraphic(null);
            return;
        }
        SVGPath icon = new SVGPath();
        icon.setContent(LOCK);
        icon.getStyleClass().add("project-lock");
        Tooltip.install(icon, new Tooltip("Закрытый проект: видят только участники и администраторы"));
        label.setGraphic(icon);
        label.setContentDisplay(ContentDisplay.RIGHT);
        label.setGraphicTextGap(6);
    }
}
