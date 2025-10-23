import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class UserManager {
    private final Path sessionFile;
    private final Database db;
    private volatile String lastError = null;

    public static class User {
        public final String fullName;
        public final String idType;
        public final String idNumber;
        public final String email;
        public final String phone;
        public final String password; // stored in plain text per current requirement

        public User(String fullName, String idType, String idNumber, String email, String phone, String password) {
            this.fullName = fullName;
            this.idType = idType;
            this.idNumber = idNumber;
            this.email = email;
            this.phone = phone;
            this.password = password;
        }
    }

    public UserManager(Database db) {
        this.db = db;
        this.sessionFile = Paths.get("session.txt");
    }

    public boolean register(User user) {
        String sql = "INSERT INTO users(full_name, id_type, id_number, email, phone, password_hash, allow_location) VALUES(?,?,?,?,?,?,1)";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.fullName);
            ps.setString(2, user.idType);
            ps.setString(3, user.idNumber);
            ps.setString(4, user.email);
            ps.setString(5, user.phone);
            ps.setString(6, PasswordUtil.hashPassword(user.password.toCharArray()));
            ps.executeUpdate();
            saveSession(user.email);
            lastError = null;
            return true;
        } catch (SQLException e) {
            // Duplicate email (unique key) or other errors
            String sqlState = e.getSQLState();
            if (sqlState != null && (sqlState.equals("23000") || sqlState.startsWith("23"))) {
                // Integrity constraint violation
                System.err.println("Register failed - duplicate or constraint: " + e.getMessage());
                lastError = "Email already registered (constraint).";
            } else {
                System.err.println("Register failed: " + e.getMessage());
                lastError = e.getMessage();
            }
            return false;
        }
    }

    public boolean login(String email, String password) {
        String sql = "SELECT password_hash FROM users WHERE email = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean ok = PasswordUtil.verifyPassword(password.toCharArray(), rs.getString(1));
                    if (ok) { saveSession(email); lastError = null; }
                    else lastError = "Invalid email or password.";
                    return ok;
                }
                lastError = "User not found.";
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Login failed: " + e.getMessage());
            lastError = e.getMessage();
            return false;
        }
    }

    public User getUser(String email) {
        String sql = "SELECT full_name, id_type, id_number, email, phone, password_hash FROM users WHERE email = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getString(1), // full_name
                            rs.getString(2), // id_type
                            rs.getString(3), // id_number
                            rs.getString(4), // email
                            rs.getString(5), // phone
                            rs.getString(6)  // password_hash (not used directly)
                    );
                }
                return null;
            }
        } catch (SQLException e) {
            System.err.println("getUser failed: " + e.getMessage());
            return null;
        }
    }

    public Optional<User> getCurrentSessionUser() {
        if (!Files.exists(sessionFile)) return Optional.empty();
        try {
            String email = new String(Files.readAllBytes(sessionFile), StandardCharsets.UTF_8).trim();
            if (email.isEmpty()) return Optional.empty();
            return Optional.ofNullable(getUser(email));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public void saveSession(String email) {
        try {
            Files.write(sessionFile, email.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            System.err.println("Failed to save session: " + ex.getMessage());
        }
    }

    public void clearSession() {
        try {
            if (Files.exists(sessionFile)) {
                Files.delete(sessionFile);
            }
        } catch (IOException ex) {
            System.err.println("Failed to clear session: " + ex.getMessage());
        }
    }

    public String getLastError() {
        return lastError;
    }
}
