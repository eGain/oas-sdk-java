package egain.oassdk.core.logging;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.Locale;

/**
 * Configurable logging utility using java.util.logging
 * <p>
 * Provides centralized logging configuration with:
 * - File-based logging with automatic rotation
 * - Configurable log directory, file size, and rotation settings
 * - Support for system properties and environment variables
 */
public class LoggerConfig {
    
    private static final String DEFAULT_LOG_DIR = "logs";
    private static final String DEFAULT_LOG_FILE = "oas-sdk.log";
    private static final int DEFAULT_MAX_FILE_SIZE = 1024 * 1024; // 1 MB in bytes
    private static final int DEFAULT_MAX_BACKUP_FILES = 10;
    private static final String DEFAULT_LOG_LEVEL = "INFO";
    
    private static final String LOG_DIR_PROPERTY = "oas.sdk.log.dir";
    private static final String LOG_FILE_PROPERTY = "oas.sdk.log.file";
    private static final String LOG_SIZE_PROPERTY = "oas.sdk.log.size";
    private static final String LOG_BACKUPS_PROPERTY = "oas.sdk.log.backups";
    private static final String LOG_LEVEL_PROPERTY = "oas.sdk.log.level";
    
    private static volatile boolean initialized = false;
    private static volatile String logDirectory;
    private static volatile String logFileName;
    private static volatile int maxFileSize;
    private static volatile int maxBackupFiles;
    private static volatile Level logLevel;
    
    // Fallback logger for initialization phase (before logging is fully configured)
    private static final Logger fallbackLogger = Logger.getLogger(LoggerConfig.class.getName());
    
    /**
     * Initialize logging configuration
     * <p>
     * Configuration is loaded in the following order (highest priority first):
     * 1. System properties: oas.sdk.log.dir, oas.sdk.log.file, oas.sdk.log.size, etc.
     * 2. Environment variables: OAS_SDK_LOG_DIR, OAS_SDK_LOG_FILE, etc.
     * 3. logger.properties file in classpath
     * 4. Default values if not specified
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        // Load configuration from properties file first
        Properties props = loadPropertiesFile();
        
        // Load configuration with priority: system property > env variable > properties file > default
        logDirectory = getProperty(LOG_DIR_PROPERTY, "OAS_SDK_LOG_DIR", 
                props.getProperty("log.dir", DEFAULT_LOG_DIR));
        logFileName = getProperty(LOG_FILE_PROPERTY, "OAS_SDK_LOG_FILE", 
                props.getProperty("log.file", DEFAULT_LOG_FILE));
        
        String sizeStr = getProperty(LOG_SIZE_PROPERTY, "OAS_SDK_LOG_SIZE", 
                props.getProperty("log.size", null));
        if (sizeStr != null) {
            try {
                maxFileSize = parseSize(sizeStr);
            } catch (NumberFormatException e) {
                fallbackLogger.warning("Invalid log size format: " + sizeStr + ", using default: 1MB");
                maxFileSize = DEFAULT_MAX_FILE_SIZE;
            }
        } else {
            maxFileSize = DEFAULT_MAX_FILE_SIZE;
        }
        
        String backupsStr = getProperty(LOG_BACKUPS_PROPERTY, "OAS_SDK_LOG_BACKUPS", 
                props.getProperty("log.backups", null));
        if (backupsStr != null) {
            try {
                maxBackupFiles = Integer.parseInt(backupsStr);
            } catch (NumberFormatException e) {
                fallbackLogger.warning("Invalid backup files count: " + backupsStr + ", using default: 10");
                maxBackupFiles = DEFAULT_MAX_BACKUP_FILES;
            }
        } else {
            maxBackupFiles = DEFAULT_MAX_BACKUP_FILES;
        }
        
        String levelStr = getProperty(LOG_LEVEL_PROPERTY, "OAS_SDK_LOG_LEVEL", 
                props.getProperty("log.level", DEFAULT_LOG_LEVEL));
        try {
            logLevel = Level.parse(levelStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            fallbackLogger.warning("Invalid log level: " + levelStr + ", using default: INFO");
            logLevel = Level.INFO;
        }
        
        // Create log directory if it doesn't exist
        try {
            Path logDirPath = Paths.get(logDirectory);
            if (!Files.exists(logDirPath)) {
                Files.createDirectories(logDirPath);
            }
        } catch (IOException e) {
            fallbackLogger.warning("Failed to create log directory: " + logDirectory + ", using default");
            logDirectory = DEFAULT_LOG_DIR;
            try {
                Files.createDirectories(Paths.get(logDirectory));
            } catch (IOException ex) {
                fallbackLogger.severe("Failed to create default log directory: " + ex.getMessage());
                return;
            }
        }
        
        // Configure root logger
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        
        // Remove existing handlers
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }
        
        // Create file handler with rotation
        try {
            String logFilePath = Paths.get(logDirectory, logFileName).toString();
            RotatingFileHandler fileHandler = RotatingFileHandler.create(
                logFilePath,
                maxFileSize,
                maxBackupFiles
            );
            
            // Set formatter
            fileHandler.setFormatter(new LogFormatter());
            
            // Set level
            fileHandler.setLevel(logLevel);
            
            // Add handler to root logger
            rootLogger.addHandler(fileHandler);
            rootLogger.setLevel(logLevel);
            
            // Also add console handler for immediate feedback (optional, can be disabled)
            String consoleLogging = getProperty("oas.sdk.log.console", "OAS_SDK_LOG_CONSOLE", 
                    props.getProperty("log.console", "true"));
            if ("true".equalsIgnoreCase(consoleLogging)) {
                java.util.logging.ConsoleHandler consoleHandler = new java.util.logging.ConsoleHandler();
                consoleHandler.setFormatter(new LogFormatter());
                consoleHandler.setLevel(logLevel);
                rootLogger.addHandler(consoleHandler);
            }
            
        } catch (IOException e) {
            fallbackLogger.log(Level.SEVERE, "Failed to initialize file logging: " + e.getMessage(), e);
        }
        
        initialized = true;
    }
    
    /**
     * Get a logger for the specified class
     * 
     * @param clazz The class requesting the logger
     * @return Logger instance
     */
    public static Logger getLogger(Class<?> clazz) {
        if (!initialized) {
            initialize();
        }
        return Logger.getLogger(clazz.getName());
    }
    
    /**
     * Get a logger for the specified name
     * 
     * @param name The name for the logger
     * @return Logger instance
     */
    public static Logger getLogger(String name) {
        if (!initialized) {
            initialize();
        }
        return Logger.getLogger(name);
    }
    
    /**
     * Load logger properties from classpath
     */
    private static Properties loadPropertiesFile() {
        Properties props = new Properties();
        try (InputStream is = LoggerConfig.class.getClassLoader().getResourceAsStream("logger.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // Properties file not found or error reading - use defaults
            // This is expected if logger.properties doesn't exist
        }
        return props;
    }
    
    /**
     * Get property value from system properties or environment variables
     */
    private static String getProperty(String systemProperty, String envVariable, String defaultValue) {
        String value = System.getProperty(systemProperty);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        
        value = System.getenv(envVariable);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        
        return defaultValue;
    }
    
    /**
     * Parse size string (e.g., "1MB", "1024KB", "1048576")
     * Supports: KB, MB, GB suffixes
     */
    private static int parseSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) {
            return DEFAULT_MAX_FILE_SIZE;
        }
        
        sizeStr = sizeStr.trim().toUpperCase(Locale.ROOT);
        
        if (sizeStr.endsWith("KB")) {
            int value = Integer.parseInt(sizeStr.substring(0, sizeStr.length() - 2));
            return value * 1024;
        } else if (sizeStr.endsWith("MB")) {
            int value = Integer.parseInt(sizeStr.substring(0, sizeStr.length() - 2));
            return value * 1024 * 1024;
        } else if (sizeStr.endsWith("GB")) {
            int value = Integer.parseInt(sizeStr.substring(0, sizeStr.length() - 2));
            return value * 1024 * 1024 * 1024;
        } else {
            return Integer.parseInt(sizeStr);
        }
    }
    
    /**
     * Custom file handler that rotates based on file size
     */
    private static class RotatingFileHandler extends Handler {
        private final String baseFileName;
        private final Path baseFilePath;
        private final long maxFileSize;
        private final int maxBackupFiles;
        private FileHandler currentHandler;
        private int currentFileIndex = 0;
        private static final Logger handlerLogger = Logger.getLogger(RotatingFileHandler.class.getName());
        
        private RotatingFileHandler(String baseFileName, long maxFileSize, int maxBackupFiles) {
            this.baseFileName = baseFileName;
            this.baseFilePath = Paths.get(baseFileName);
            this.maxFileSize = maxFileSize;
            this.maxBackupFiles = maxBackupFiles;
        }

        private static RotatingFileHandler create(String baseFileName, long maxFileSize, int maxBackupFiles) throws IOException {
            RotatingFileHandler handler = new RotatingFileHandler(baseFileName, maxFileSize, maxBackupFiles);
            handler.rotateIfNeeded();
            return handler;
        }
        
        @Override
        public synchronized void publish(LogRecord record) {
            if (!isLoggable(record)) {
                return;
            }
            
            // Check if rotation is needed before publishing
            try {
                rotateIfNeeded();
            } catch (IOException e) {
                // Log error but continue
                handlerLogger.log(Level.WARNING, "Error during log rotation: " + e.getMessage(), e);
            }
            
            // Publish to current handler
            if (currentHandler != null) {
                currentHandler.publish(record);
                currentHandler.flush();
            }
        }
        
        @Override
        public synchronized void flush() {
            if (currentHandler != null) {
                currentHandler.flush();
            }
        }
        
        @Override
        public synchronized void close() throws SecurityException {
            if (currentHandler != null) {
                currentHandler.close();
            }
        }
        
        private void rotateIfNeeded() throws IOException {
            Path currentFilePath = getCurrentFilePath();
            
            // Check if file exists and exceeds size limit
            if (Files.exists(currentFilePath) && Files.size(currentFilePath) >= maxFileSize) {
                // Close current handler
                if (currentHandler != null) {
                    currentHandler.close();
                }
                
                // Rotate files
                rotateFiles();
                
                // Create new handler
                currentHandler = new FileHandler(baseFileName, true);
                Formatter formatter = getFormatter();
                if (formatter != null) {
                    currentHandler.setFormatter(formatter);
                }
                Level level = getLevel();
                if (level != null) {
                    currentHandler.setLevel(level);
                }
            } else if (currentHandler == null) {
                // First time initialization
                currentHandler = new FileHandler(baseFileName, true);
                Formatter formatter = getFormatter();
                if (formatter != null) {
                    currentHandler.setFormatter(formatter);
                }
                Level level = getLevel();
                if (level != null) {
                    currentHandler.setLevel(level);
                }
            }
        }
        
        private void rotateFiles() {
            try {
                // Delete oldest file if we've reached max backups
                if (currentFileIndex >= maxBackupFiles) {
                    Path oldestFilePath = getFilePath(maxBackupFiles);
                    if (Files.exists(oldestFilePath)) {
                        Files.deleteIfExists(oldestFilePath);
                    }
                    
                    // Shift all files down
                    for (int i = maxBackupFiles; i > 0; i--) {
                        Path sourcePath = getFilePath(i - 1);
                        Path destPath = getFilePath(i);
                        if (Files.exists(sourcePath)) {
                            if (Files.exists(destPath)) {
                                Files.deleteIfExists(destPath);
                            }
                            Files.move(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } else {
                    // Just rename current file
                    Path currentFilePath = getCurrentFilePath();
                    if (Files.exists(currentFilePath)) {
                        Path rotatedFilePath = getFilePath(currentFileIndex);
                        if (Files.exists(rotatedFilePath)) {
                            Files.deleteIfExists(rotatedFilePath);
                        }
                        Files.move(currentFilePath, rotatedFilePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    currentFileIndex++;
                }
            } catch (IOException e) {
                handlerLogger.log(Level.WARNING, "Error during file rotation: " + e.getMessage(), e);
            }
        }
        
        private Path getCurrentFilePath() {
            return baseFilePath;
        }
        
        private Path getFilePath(int index) {
            if (index == 0) {
                return baseFilePath;
            }
            return Paths.get(baseFileName + "." + index);
        }
    }
    
    /**
     * Custom log formatter for better readability
     */
    private static class LogFormatter extends Formatter {
        private static final String FORMAT = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS [%2$-7s] %3$s: %4$s%5$s%n";
        
        @Override
        public String format(LogRecord record) {
            String source = record.getSourceClassName();
            if (source != null) {
                // Extract simple class name
                int lastDot = source.lastIndexOf('.');
                if (lastDot >= 0) {
                    source = source.substring(lastDot + 1);
                }
            } else {
                source = record.getLoggerName();
            }
            
            String message = formatMessage(record);
            String throwable = "";
            
            if (record.getThrown() != null) {
                throwable = "\n" + getStackTrace(record.getThrown());
            }
            
            return String.format(FORMAT,
                record.getMillis(),
                record.getLevel(),
                source,
                message,
                throwable
            );
        }
        
        private String getStackTrace(Throwable throwable) {
            java.io.StringWriter sw = new java.io.StringWriter();
            try (java.io.PrintWriter pw = new java.io.PrintWriter(sw)) {
                throwable.printStackTrace(pw);
            }
            return sw.toString();
        }
    }
    
    /**
     * Get current log directory
     */
    public static String getLogDirectory() {
        return logDirectory;
    }
    
    /**
     * Get current log file name
     */
    public static String getLogFileName() {
        return logFileName;
    }
    
    /**
     * Get maximum file size in bytes
     */
    public static int getMaxFileSize() {
        return maxFileSize;
    }
    
    /**
     * Get maximum number of backup files
     */
    public static int getMaxBackupFiles() {
        return maxBackupFiles;
    }
    
    /**
     * Get current log level
     */
    public static Level getLogLevel() {
        return logLevel;
    }
}

