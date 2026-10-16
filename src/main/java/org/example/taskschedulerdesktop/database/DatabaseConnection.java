package org.example.taskschedulerdesktop.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.example.taskschedulerdesktop.config.DbConfig;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);

    private static DatabaseConnection instance;
    private final HikariDataSource dataSource;
    private Server h2Server;

    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private static final String H2_TCP_PROPERTY = "taskhub.h2.tcp";
    private static final String H2_TCP_PORT = "9092";

    private DatabaseConnection() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DbConfig.getDbUrl());
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(5);
        config.setPoolName("TaskSchedulerPool");

        this.dataSource = new HikariDataSource(config);
        log.info("HikariCP initialized for {}", DbConfig.getDbUrl());

        // TCP-сервер нужен только для просмотра БД из IDE во время разработки.
        // Включается флагом -Dtaskhub.h2.tcp=true, доступен только с localhost.
        if (Boolean.getBoolean(H2_TCP_PROPERTY)) {
            startH2TcpServer();
        }
    }

    private void startH2TcpServer() {
        try {
            this.h2Server = Server.createTcpServer("-tcp", "-tcpPort", H2_TCP_PORT).start();
            log.info("H2 TCP server started on localhost:{}", H2_TCP_PORT);
        } catch (SQLException e) {
            log.warn("Failed to start H2 TCP server on port {}", H2_TCP_PORT, e);
        }
    }

    public static synchronized DatabaseConnection getInstance() {
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