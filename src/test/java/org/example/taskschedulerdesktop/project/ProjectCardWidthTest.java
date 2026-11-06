package org.example.taskschedulerdesktop.project;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ширина карточек проектов: на большом мониторе три в ряд, на ноутбуке две. */
class ProjectCardWidthTest {

    private static final double GAP = 20;

    /** Сколько карточек такой ширины поместится в ряд. */
    private static int columns(double available) {
        double width = ProjectsController.cardWidth(available, GAP);
        return (int) Math.floor((available + GAP) / (width + GAP));
    }

    @Test
    void wideMonitorFitsThreeFullWidthCards() {
        // 2560 px: минус меню 220 и поля страницы 2 × 32
        assertEquals(620, ProjectsController.cardWidth(2276, GAP));
        assertEquals(3, columns(2276));
    }

    @Test
    void laptopFitsTwoCards() {
        assertEquals(2, columns(1252)); // 1536 px (1920 при масштабе 125%)
        assertEquals(2, columns(1082)); // 1366 px
        assertEquals(2, columns(996));  // 1280 px (1920 при масштабе 150%)
        assertTrue(ProjectsController.cardWidth(996, GAP) >= ProjectsController.MIN_CARD_WIDTH);
    }

    @Test
    void narrowWindowShowsOneCardThatFits() {
        assertEquals(1, columns(700));
        assertEquals(400, ProjectsController.cardWidth(400, GAP));
    }
}
