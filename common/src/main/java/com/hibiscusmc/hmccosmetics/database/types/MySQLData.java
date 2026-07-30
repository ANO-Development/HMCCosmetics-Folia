package com.hibiscusmc.hmccosmetics.database.types;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.section.DatabaseSettings;
import com.hibiscusmc.hmccosmetics.database.Database;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;

public class MySQLData extends SQLData {

    // Connection Information
    private String host;
    private String user;
    private String database;
    private String password;
    private int port;

    @Nullable
    private Connection connection;

    @Override
    public void setup() {
        host = DatabaseSettings.getHost();
        user = DatabaseSettings.getUsername();
        database = DatabaseSettings.getDatabase();
        password = DatabaseSettings.getPassword();
        port = DatabaseSettings.getPort();

        try {
            openConnection();
            if (connection == null) throw new IllegalStateException("Connection is null");
            try (PreparedStatement preparedStatement =  connection.prepareStatement("CREATE TABLE IF NOT EXISTS `COSMETICDATABASE` " +
                    "(UUID varchar(36) PRIMARY KEY, " +
                    "COSMETICS MEDIUMTEXT " +
                    ");")) {
                preparedStatement.execute();
            }
        } catch (SQLException | IllegalStateException exception) {
            throw new IllegalStateException("The configured MySQL database could not be initialized.", exception);
        }
    }

    @Override
    public void clear(UUID uniqueId) {
        Database.execute(() -> {
            try (PreparedStatement preparedSt = preparedStatement("DELETE FROM COSMETICDATABASE WHERE UUID=?;")) {
                preparedSt.setString(1, uniqueId.toString());
                preparedSt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }).exceptionally(exception -> {
            HMCCosmeticsPlugin.getInstance().getLogger().log(Level.SEVERE, "Unable to clear cosmetic data for " + uniqueId + ".", exception);
            return null;
        });
    }

    private void openConnection() throws SQLException {
        // Connection isn't null AND Connection isn't closed :: return
        if (isConnectionOpen()) return;
        if (connection != null) close();

        // Connect to database host
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, setupProperties());
        } catch (ClassNotFoundException exception) {
            throw new SQLException("The MySQL JDBC driver is unavailable.", exception);
        }
    }

    @Override
    public void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException exception) {
            HMCCosmeticsPlugin.getInstance().getLogger().log(Level.WARNING, "Unable to close the MySQL connection cleanly.", exception);
        } finally {
            connection = null;
        }
    }

    @NotNull
    private Properties setupProperties() {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        return props;
    }

    private boolean isConnectionOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PreparedStatement preparedStatement(String query) {
        PreparedStatement ps = null;

        if (!isConnectionOpen()) {
            MessagesUtil.sendDebugMessages("The MySQL database connection is not open (Could the database been idle for to long?). Reconnecting...", Level.WARNING);
            try {
                openConnection();
            } catch (SQLException exception) {
                throw new IllegalStateException("Unable to reconnect to MySQL.", exception);
            }
        }

        try {
            if (connection == null) throw new IllegalStateException("Connection is null");
            ps = connection.prepareStatement(query);
        } catch (SQLException | IllegalStateException exception) {
            throw new IllegalStateException("Unable to prepare a MySQL statement.", exception);
        }

        return ps;
    }
}
