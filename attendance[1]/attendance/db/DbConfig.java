package com.attendance.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbConfig {
    private static final Logger logger = LoggerFactory.getLogger(DbConfig.class);
    private static final HikariDataSource dataSource;

    static {
        try {
            Properties dbProps = new Properties();
            Properties poolProps = new Properties();
            try (InputStream dbIs = DbConfig.class.getClassLoader().getResourceAsStream("config/db.properties")) {
                if (dbIs == null) {
                    logger.error("Could not find config/db.properties in classpath");
                    throw new RuntimeException("config/db.properties not found");
                }
                dbProps.load(dbIs);
                logger.info("Loaded config/db.properties successfully");
            }
            try (InputStream poolIs = DbConfig.class.getClassLoader().getResourceAsStream("config/dbpool.properties")) {
                if (poolIs == null) {
                    logger.error("Could not find config/dbpool.properties in classpath");
                    throw new RuntimeException("config/dbpool.properties not found");
                }
                poolProps.load(poolIs);
                logger.info("Loaded config/dbpool.properties successfully");
            }
            
            HikariConfig config = new HikariConfig();
            String jdbcUrl = dbProps.getProperty("jdbcUrl");
            String username = dbProps.getProperty("username");
            String password = dbProps.getProperty("password");
            
            logger.info("Configuring database connection with URL: {}, username: {}", jdbcUrl, username);
            
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(
                Integer.parseInt(poolProps.getProperty("maximumPoolSize"))
            );
            config.setIdleTimeout(
                Long.parseLong(poolProps.getProperty("idleTimeout"))
            );
            
            logger.info("Attempting to create connection pool...");
            dataSource = new HikariDataSource(config);
            logger.info("Connection pool created successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize DataSource", e);
            throw new RuntimeException("Failed to initialize DataSource", e);
        }
    }

    public static DataSource getDataSource() {
        return dataSource;
    }
}
