package org.example.taskschedulerdesktop.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {

    private static DatabaseConnection instance;
    private final HikariDataSource dataSource;
    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);

    private static final String URL = "jdbc:h2:~/tasks-db"; //;AUTO_SERVER=TRUE
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private Server h2Server;

    private DatabaseConnection() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASSWORD);

        config.setConnectionTestQuery("SELECT 1");
        config.setMaximumPoolSize(5);
        config.setPoolName("TaskSchedulerPool");

        this.dataSource = new HikariDataSource(config);

        try {
            this.h2Server = Server.createTcpServer("-tcp", "-tcpPort", "9092", "-tcpAllowOthers").start();
            log.debug("H2 TCP-server started on the port:9092 successful");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to start H2Server");
        }
    }

    public static DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException{
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
            log.info("HikariCP connection pool closed");
        }
        if (h2Server != null) {
            h2Server.stop();
            log.info("H2 TCP-sever closed");
        }
    }
}