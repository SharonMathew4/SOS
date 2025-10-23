import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public class Database {
    private final String url;
    private final String user;
    private final String password;

    public Database(String propertiesPath) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(propertiesPath)) {
            props.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DB properties from " + propertiesPath + ": " + e.getMessage(), e);
        }
        this.url = props.getProperty("db.url");
        this.user = props.getProperty("db.user");
        this.password = props.getProperty("db.password");
        if (this.url == null || this.url.isBlank() || this.user == null || this.user.isBlank() || this.password == null) {
            throw new RuntimeException("Database configuration missing. Please fill db.properties with db.url, db.user, and db.password.");
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC Driver not found. Add mysql-connector-j to classpath.", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return user;
    }

    public Integer getUserIdByEmail(String email) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return null;
            }
        }
    }

    public void saveLocation(String email, double lat, double lon) {
        String sql = "INSERT INTO locations(user_id, latitude, longitude) SELECT id, ?, ? FROM users WHERE email = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, lat);
            ps.setDouble(2, lon);
            ps.setString(3, email);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to save location: " + e.getMessage());
        }
    }
}
