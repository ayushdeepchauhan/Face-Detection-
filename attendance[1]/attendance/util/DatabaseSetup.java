package com.attendance.util;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Utility class to set up the database schema.
 */
public class DatabaseSetup {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSetup.class);
    
    /**
     * Executes the SQL script to create the necessary database tables.
     */
    public static void setupDatabase() {
        logger.info("Setting up database schema...");
        
        // Initialize DataSource
        javax.sql.DataSource dataSource = com.attendance.db.DbConfig.getDataSource();
        
        try {
            // Execute the SQL script
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                // Read the SQL script from resources
                String sql = readResourceFile("/schema_courses.sql");
                
                // Execute the SQL statements
                stmt.execute(sql);
                
                logger.info("Database schema setup completed successfully");
            }
        } catch (SQLException e) {
            logger.error("Error setting up database schema", e);
            throw new RuntimeException("Error setting up database schema", e);
        } finally {
            // Close the data source to prevent resource leak
            if (dataSource != null) {
                
            }
        }
    }
    
    /**
     * Reads a resource file and returns its contents as a string.
     * 
     * @param resourcePath The path to the resource file
     * @return The contents of the resource file as a string
     */
    private static String readResourceFile(String resourcePath) {
        try (InputStream is = DatabaseSetup.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            logger.error("Error reading resource file: {}", resourcePath, e);
            throw new RuntimeException("Error reading resource file: " + resourcePath, e);
        }
    }
    
    /**
     * Main method to run the database setup.
     */
    public static void main(String[] args) {
        // Set logging levels to reduce verbosity
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "WARN");
        System.setProperty("org.slf4j.simpleLogger.log.com.zaxxer.hikari", "WARN");
        System.setProperty("org.slf4j.simpleLogger.log.com.attendance", "INFO");
        
        setupDatabase();
    }
}
