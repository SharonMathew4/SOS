import javax.swing.*;
import java.sql.*;

public class ContactManager {
    private final DefaultListModel<ContactModel> contactsListModel = new DefaultListModel<>();
    private final Database db;
    private final String userEmail;

    public ContactManager(Database db, String userEmail) {
        this.db = db;
        this.userEmail = userEmail;
        loadContactsFromDb();
    }

    public DefaultListModel<ContactModel> getContactsListModel() {
        return contactsListModel;
    }

    public void addContact(String name, String phone) {
        String sql = "INSERT INTO contacts(user_id, name, phone) SELECT id, ?, ? FROM users WHERE email=?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, userEmail);
            ps.executeUpdate();
            contactsListModel.addElement(new ContactModel(name, phone));
        } catch (SQLException e) {
            System.err.println("Failed to add contact: " + e.getMessage());
        }
    }

    public void deleteContact(ContactModel contact) {
        String sql = "DELETE FROM contacts WHERE user_id = (SELECT id FROM users WHERE email = ?) AND name = ? AND phone = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userEmail);
            ps.setString(2, contact.getName());
            ps.setString(3, contact.getPhoneNumber());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                contactsListModel.removeElement(contact);
            }
        } catch (SQLException e) {
            System.err.println("Failed to delete contact: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void editContact(ContactModel oldContact, String newName, String newPhone) {
        String sql = "UPDATE contacts SET name = ?, phone = ? WHERE user_id = (SELECT id FROM users WHERE email = ?) AND name = ? AND phone = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setString(2, newPhone);
            ps.setString(3, userEmail);
            ps.setString(4, oldContact.getName());
            ps.setString(5, oldContact.getPhoneNumber());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                // Update in model
                int idx = contactsListModel.indexOf(oldContact);
                if (idx != -1) {
                    contactsListModel.set(idx, new ContactModel(newName, newPhone));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to edit contact: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadContactsFromDb() {
        contactsListModel.clear();
        String sql = "SELECT c.name, c.phone FROM contacts c JOIN users u ON u.id = c.user_id WHERE u.email = ? ORDER BY c.id DESC";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userEmail);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    contactsListModel.addElement(new ContactModel(rs.getString(1), rs.getString(2)));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load contacts: " + e.getMessage());
        }
    }
}