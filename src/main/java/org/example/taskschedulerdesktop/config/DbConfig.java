package org.example.taskschedulerdesktop.config;

import java.io.File;

public final class DbConfig {

    private DbConfig() {}

    public static String getDbUrl() {
        String userHome = System.getProperty("user.home");
        String dataDir = userHome + File.separator + ".taskschedulerdb" + File.separator + "data";

        File dir = new File(dataDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                throw new IllegalStateException("Failed to create data directory: " + dataDir);
            }
        }

        String dbPath = dataDir + File.separator + "taskschedulerdb";

        return "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=-1";
    }
}
