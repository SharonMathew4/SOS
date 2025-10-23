import java.awt.*;
import javax.swing.*;
import java.net.URI;
import java.net.URLEncoder;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.swing.border.*;
import java.awt.event.*;
import java.util.HashMap;

public class Main extends JFrame {
    // Components
    // Removed manual phone input; SOS uses saved contacts
    private JButton sosButton;
    private JLabel statusLabel;
    private JPanel contentPanel;
    private CardLayout cardLayout;
    private HashMap<String, JPanel> panels;
    private ContactManager contactManager;
    private Database database;
    private UserManager userManager;
    private UserManager.User currentUser;
    private JList<ContactModel> contactsList;
    

    // Navigation items
    private final String[] NAV_ITEMS = {"Emergency", "Contacts", "Profile", "Settings", "Help"};
    private final String[] NAV_ICONS = {"🆘", "👥", "👤", "⚙️", "❓"};

    // Data
    private double latitude = 0.0;
    private double longitude = 0.0;
    
    // Settings
    // Template: include user's full name when available. The app will append the location link after this text.
    private String defaultMessage = "Hello, EMERGENCY... I am <name>. I need help... My location:";
    private boolean autoLocation = true;
    
    public Main() {
        // Load custom font and apply modern style
        loadAndApplyCustomFont();
        applyModernStyle();

        // Initialize components
        panels = new HashMap<>();
        cardLayout = new CardLayout();
    try {
        database = new Database("db.properties");
        userManager = new UserManager(database);
        currentUser = userManager.getCurrentSessionUser().orElse(null);
    } catch (RuntimeException ex) {
        JOptionPane.showMessageDialog(null,
                "Database not configured or unreachable.\n" +
                ex.getMessage() + "\n\n" +
                "Open db.properties in the project folder and set db.url, db.user, db.password,\n" +
                "then run the app again.",
                "Database Configuration Required",
                JOptionPane.ERROR_MESSAGE);
        System.exit(1);
        return;
    }
    // Instantiate contact manager lazily after we know the current user
    if (currentUser != null) {
        contactManager = new ContactManager(database, currentUser.email);
    } else {
        contactManager = new ContactManager(database, ""); // temporary, will be replaced after login
    }
    contactsList = new JList<>(contactManager.getContactsListModel());
        
        // Set up the frame
    setTitle("Save Our Ship");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(900, 600));
        getContentPane().setBackground(AppStyles.BACKGROUND_COLOR);

        // Create main content area
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(AppStyles.BACKGROUND_COLOR);
        
        // Create sidebar
        JPanel sidebarPanel = new JPanel();
        sidebarPanel.setPreferredSize(new Dimension(200, 0));
        sidebarPanel.setBackground(AppStyles.SIDEBAR_COLOR);
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBorder(new EmptyBorder(15, 0, 15, 0));

        // Add navigation items to sidebar
        for (int i = 0; i < NAV_ITEMS.length; i++) {
            JPanel navItem = createNavItem(NAV_ITEMS[i], NAV_ICONS[i]);
            sidebarPanel.add(navItem);
            sidebarPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        }
        
        // Create main layout
        setLayout(new BorderLayout(0, 0));
        add(sidebarPanel, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);
        
        // Create all content panels
        createEmergencyPanel();
        createContactsPanel();
        createSettingsPanel();
        createHelpPanel();
        createProfilePanel();

        // If no session, show login/register dialog first
        if (currentUser == null) {
            showAuthDialog();
        }

        // Show default panel
        cardLayout.show(contentPanel, "Emergency");

    // Update location
    updateLocation();
        
        // Pack and center the frame
        pack();
        setLocationRelativeTo(null);
    }

    private void sendSOS() {
        // Send SOS to all saved contacts via WhatsApp Web links
        try {
            // Prepare message by replacing placeholder with user's full name if available
            String template = defaultMessage;
            if (currentUser != null && currentUser.fullName != null && !currentUser.fullName.isBlank()) {
                template = template.replace("<name>", currentUser.fullName);
            } else {
                template = template.replace("<name>", "");
            }
            // Ensure we append the location URL after the template text
            String message = template + " " + String.format("My location: https://www.google.com/maps?q=%f,%f", latitude, longitude);
            DefaultListModel<ContactModel> model = contactManager.getContactsListModel();
            if (model.getSize() == 0) {
                statusLabel.setText("No saved contacts to send SOS to");
                return;
            }

            final Desktop desktop;
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                desktop = Desktop.getDesktop();
            } else {
                desktop = null;
            }

            // Show confirmation dialog
            int contactCount = model.getSize();
            int confirm = JOptionPane.showConfirmDialog(
                this,
                "This will open " + contactCount + " WhatsApp tab(s).\nYou will need to manually click 'Send' for each contact.\n\nContinue?",
                "Send Emergency SOS",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (confirm != JOptionPane.YES_OPTION) {
                statusLabel.setText("SOS cancelled");
                return;
            }

            statusLabel.setText("Opening WhatsApp for " + contactCount + " contact(s)...");
            
            // Open WhatsApp tabs with delay for better browser handling
            new Thread(() -> {
                java.util.List<String> invalidNumbers = new java.util.ArrayList<>();
                int opened = 0;
                for (int i = 0; i < model.getSize(); i++) {
                    ContactModel cm = model.getElementAt(i);
                    String rawPhone = cm.getPhoneNumber().trim();
                    if (rawPhone.isEmpty()) continue;

                    String normalized = normalizePhone(rawPhone);
                    if (normalized == null) {
                        invalidNumbers.add(cm.getName() + " (" + rawPhone + ")");
                        continue;
                    }

                    try {
                        String encoded = URLEncoder.encode(message, "UTF-8");
                        boolean openedThis = false;
                        // First try whatsapp protocol (WhatsApp Desktop)
                        try {
                            String desktopUri = "whatsapp://send?phone=" + normalized + "&text=" + encoded;
                            if (desktop != null) {
                                desktop.browse(new URI(desktopUri));
                                openedThis = true;
                            }
                        } catch (Exception ex1) {
                            // If on Windows, try shell start as another fallback (may open registered app)
                            try {
                                String os = System.getProperty("os.name").toLowerCase();
                                if (os.contains("win")) {
                                    String cmd = "cmd /c start \"\" \"whatsapp://send?phone=" + normalized + "&text=" + encoded + "\"";
                                    Runtime.getRuntime().exec(cmd);
                                    openedThis = true;
                                }
                            } catch (Exception ex2) {
                                // ignore and fall back to web
                            }
                        }

                        // Final fallback to web.whatsapp.com
                        if (!openedThis) {
                            try {
                                String webUri = "https://web.whatsapp.com/send?phone=" + normalized + "&text=" + encoded;
                                if (desktop != null) {
                                    desktop.browse(new URI(webUri));
                                    openedThis = true;
                                }
                            } catch (Exception ex3) {
                                // failed
                                openedThis = false;
                            }
                        }

                        if (openedThis) {
                            opened++;
                            if (i < model.getSize() - 1) Thread.sleep(900);
                        } else {
                            invalidNumbers.add(cm.getName() + " (" + rawPhone + ") - couldn't open");
                        }

                    } catch (Exception ex) {
                        invalidNumbers.add(cm.getName() + " (" + rawPhone + ") - couldn't open");
                    }
                }

                final int sentCount = opened;
                SwingUtilities.invokeLater(() -> {
                    String statusMsg = "Opened WhatsApp tabs for " + sentCount + " contact(s).";
                    if (!invalidNumbers.isEmpty()) {
                        statusMsg += " Skipped " + invalidNumbers.size() + " invalid/failed contact(s).";
                    }
                    statusLabel.setText(statusMsg + " Click Send in each tab!");

                    if (!invalidNumbers.isEmpty()) {
                        // Show details and guidance to fix phone numbers
                        StringBuilder sb = new StringBuilder();
                        sb.append("The following contacts have invalid or problematic numbers:\n\n");
                        for (String s : invalidNumbers) sb.append("- ").append(s).append("\n");
                        sb.append("\nPlease edit these contacts and ensure phone numbers include the country code and only digits (e.g., 919876543210).\n");
                        JOptionPane.showMessageDialog(Main.this, sb.toString(), "Invalid Contacts", JOptionPane.WARNING_MESSAGE);
                    }
                });
            }).start();
            
        } catch (Exception e) {
            statusLabel.setText("Error sending SOS: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void updateLocation() {
        new Thread(() -> {
            try {
                // First try browser/device geolocation (asks user permission in the browser)
                boolean got = tryBrowserGeolocation();
                if (!got) {
                    // Fallback to IP-based geolocation
                    URL url = URI.create("http://ip-api.com/json/").toURL();
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String jsonResponse = response.toString();
                    String[] parts = jsonResponse.split(",");
                    for (String part : parts) {
                        if (part.contains("\"lat\":")) {
                            latitude = Double.parseDouble(part.split(":")[1].trim());
                        } else if (part.contains("\"lon\":")) {
                            longitude = Double.parseDouble(part.split(":")[1].trim());
                        }
                    }
                }

                if (autoLocation && currentUser != null) {
                    database.saveLocation(currentUser.email, latitude, longitude);
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Could not get location"));
            }
        }).start();
    }

    /**
     * Launches a tiny local HTTP server and serves a page that requests navigator.geolocation
     * If the user allows, the page POSTs coordinates back to /coords which this method waits for.
     * Returns true if coordinates were obtained and set into latitude/longitude.
     */
    private boolean tryBrowserGeolocation() {
        if (!Desktop.isDesktopSupported()) return false;

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            final double[] coords = new double[2];
            final CountDownLatch latch = new CountDownLatch(1);

            // Handler for root: serves HTML + JS that requests geolocation and posts to /coords
            server.createContext("/", exchange -> {
                String page = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Share location</title></head><body>"
                    + "<h3>Please allow location access in the browser to share your location with the app.</h3>"
                    + "<script>function postCoords(lat,lon){fetch('/coords',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({lat:lat,lon:lon})}).then(()=>{document.body.innerHTML='<p>Location sent. You can close this tab.</p>';}).catch(()=>{document.body.innerHTML='<p>Failed to send.</p>';});}"
                    + "if(navigator.geolocation){navigator.geolocation.getCurrentPosition(function(p){postCoords(p.coords.latitude,p.coords.longitude);},function(e){document.body.innerHTML='<p>Permission denied or unavailable.</p>';});}else{document.body.innerHTML='<p>Geolocation not supported.</p>';}</script></body></html>";
                byte[] bytes = page.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            });

            // Handler to receive coords
            server.createContext("/coords", exchange -> {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String body;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                    body = br.lines().collect(Collectors.joining("\n"));
                }
                // simple parse
                try {
                    String s = body.replaceAll("[{}\" ]", "");
                    String[] parts = s.split(",");
                    double lat = Double.NaN, lon = Double.NaN;
                    for (String p : parts) {
                        if (p.startsWith("lat:")) lat = Double.parseDouble(p.substring(4));
                        if (p.startsWith("lon:")) lon = Double.parseDouble(p.substring(4));
                    }
                    if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                        coords[0] = lat; coords[1] = lon;
                        latch.countDown();
                    }
                } catch (Exception ex) {
                    // ignore
                }
                String resp = "OK";
                byte[] rb = resp.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, rb.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(rb); }
            });

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            int port = server.getAddress().getPort();
            String url = "http://localhost:" + port + "/";

            // Open browser to request permission
            Desktop.getDesktop().browse(new URI(url));

            // Wait up to 20 seconds for user to allow and for page to POST coords
            boolean ok = latch.await(20, TimeUnit.SECONDS);
            if (ok) {
                latitude = coords[0];
                longitude = coords[1];
            }

            server.stop(0);
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    // Circular button implementation
    private static class CircularButton extends JButton {
        public CircularButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.min(getWidth(), getHeight());
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            // Drop shadow
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            g2.setColor(Color.BLACK);
            g2.fillOval(x + 4, y + 6, size - 2, size - 2);

            // Button body with gradient for 3D effect
            g2.setComposite(AlphaComposite.SrcOver);
            Color base = getModel().isArmed() ? getBackground().darker() : getBackground();
            GradientPaint gp = new GradientPaint(x, y, base.brighter(), x, y + size, base.darker());
            g2.setPaint(gp);
            g2.fillOval(x, y, size, size);

            // Inner highlight ring
            g2.setPaint(new GradientPaint(x, y, new Color(255, 255, 255, 70), x, y + size, new Color(255, 255, 255, 0)));
            g2.fillOval(x + 4, y + 4, size - 8, size - 8);

            // Rim/border
            g2.setColor(new Color(0, 0, 0, 60));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(x, y, size, size);

            // Text
            g2.setColor(getForeground());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String text = getText();
            int tx = (getWidth() - fm.stringWidth(text)) / 2;
            int ty = (getHeight() + fm.getAscent()) / 2 - 4;
            g2.drawString(text, tx, ty);

            g2.dispose();
        }

        @Override
        public boolean contains(int x, int y) {
            int size = Math.min(getWidth(), getHeight());
            int cx = getWidth()/2;
            int cy = getHeight()/2;
            double dx = x - cx;
            double dy = y - cy;
            return dx*dx + dy*dy <= (size/2.0)*(size/2.0);
        }
    }

    private JPanel createNavItem(String text, String icon) {
        JPanel item = new JPanel();
        item.setLayout(new BoxLayout(item, BoxLayout.X_AXIS));
        item.setBackground(AppStyles.SIDEBAR_COLOR);
        item.setBorder(new EmptyBorder(10, 15, 10, 15));
        item.setMaximumSize(new Dimension(200, 40));
        
        JLabel textLabel = new JLabel(text);
        textLabel.setFont(AppStyles.getAppFont(Font.PLAIN, 14f));
        textLabel.setForeground(AppStyles.TEXT_COLOR);
        
        // item.add(iconLabel);
        item.add(Box.createRigidArea(new Dimension(10, 0)));
        item.add(textLabel);
        
        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                item.setBackground(AppStyles.SIDEBAR_HOVER);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                item.setBackground(AppStyles.SIDEBAR_COLOR);
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                cardLayout.show(contentPanel, text);
            }
        });
        
        return item;
    }
    
    private void createEmergencyPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        // Emergency gradient background
        panel.setBackground(new Color(245, 245, 250));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Create SOS button
        sosButton = new CircularButton("SOS");
        sosButton.setFont(AppStyles.getAppFont(Font.BOLD, 28f));
        sosButton.setBackground(new Color(220, 53, 69)); // Emergency red
        sosButton.setForeground(Color.WHITE);
        sosButton.setFocusPainted(false);
        sosButton.setBorder(new EmptyBorder(20, 20, 20, 20));
        sosButton.setPreferredSize(new Dimension(180, 180));
        sosButton.setMaximumSize(new Dimension(180, 180));
        sosButton.setMinimumSize(new Dimension(180, 180));
        sosButton.setContentAreaFilled(false);
        sosButton.setOpaque(false);
        
        // Add hover effect
        sosButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                sosButton.setBackground(new Color(200, 35, 51));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                sosButton.setBackground(new Color(220, 53, 69));
            }
        });
        
        sosButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { sendSOS(); }
        });
        
        // Center panel with GridBagLayout for perfect centering
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(new Color(245, 245, 250));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(sosButton, gbc);
        
        // Create status section
        statusLabel = new JLabel("Ready to send emergency message", SwingConstants.CENTER);
        statusLabel.setFont(getAppFont(Font.PLAIN, 14f));
        statusLabel.setForeground(new Color(100, 100, 100));
        
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);
        
        panels.put("Emergency", panel);
        contentPanel.add(panel, "Emergency");
    }
    
    private void createContactsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(new Color(245, 245, 250));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Emergency Contacts", SwingConstants.CENTER);
        titleLabel.setFont(AppStyles.getAppFont(Font.BOLD, 26f));
        titleLabel.setForeground(new Color(220, 53, 69));

        // Create buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonsPanel.setBackground(AppStyles.BACKGROUND_COLOR);

        // Contacts List
    // Use contactManager's model
        contactsList = new JList<>(contactManager.getContactsListModel());
        contactsList.setFont(getAppFont(Font.PLAIN, 14f));
        contactsList.setCellRenderer(new ListCellRenderer<ContactModel>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends ContactModel> list, ContactModel contact, int index, boolean isSelected, boolean cellHasFocus) {
                JPanel p = new JPanel(new BorderLayout(10, 0));
                p.setBorder(new EmptyBorder(8, 8, 8, 8));
                p.setBackground(isSelected ? AppStyles.SELECTED_BACKGROUND_COLOR : AppStyles.BACKGROUND_COLOR);

                // Contact info panel (left side)
                JPanel infoPanel = new JPanel(new BorderLayout());
                infoPanel.setOpaque(false);
                JLabel nameLabel = new JLabel(contact.getName());
                nameLabel.setFont(getAppFont(Font.BOLD, 14f));
                JLabel phoneLabel = new JLabel(contact.getPhoneNumber());
                phoneLabel.setFont(getAppFont(Font.PLAIN, 12f));
                phoneLabel.setForeground(AppStyles.SECONDARY_TEXT_COLOR);
                infoPanel.add(nameLabel, BorderLayout.NORTH);
                infoPanel.add(phoneLabel, BorderLayout.SOUTH);
                p.add(infoPanel, BorderLayout.CENTER);

                // Edit and Delete buttons (right side)
                JPanel btnPanel = new JPanel();
                btnPanel.setOpaque(false);
                btnPanel.setLayout(new BoxLayout(btnPanel, BoxLayout.X_AXIS));
                JLabel editLabel = new JLabel("Edit");
                editLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                editLabel.setForeground(new Color(29, 155, 240));
                editLabel.setBorder(new EmptyBorder(0, 0, 0, 10));
                JLabel deleteLabel = new JLabel("Delete");
                deleteLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                deleteLabel.setForeground(AppStyles.DARK_RED);
                btnPanel.add(editLabel);
                btnPanel.add(deleteLabel);
                p.add(btnPanel, BorderLayout.EAST);

                return p;
            }
        });
        contactsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Mouse listener for Edit/Delete
        contactsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = contactsList.locationToIndex(e.getPoint());
                if (index != -1) {
                    ContactModel cm = contactManager.getContactsListModel().getElementAt(index);
                    Rectangle bounds = contactsList.getCellBounds(index, index);
                    int btnWidth = 90; // ~width for "EditDelete"
                    int editWidth = 40; // ~width for "Edit"
                    int xInCell = e.getX() - (int)bounds.getX();
                    if (xInCell > bounds.getWidth() - btnWidth && xInCell < bounds.getWidth() - (btnWidth - editWidth)) {
                        // Edit button clicked
                        showEditContactDialog(cm);
                    } else if (xInCell > bounds.getWidth() - (btnWidth - editWidth)) {
                        // Delete button clicked
                        int confirm = JOptionPane.showConfirmDialog(Main.this, "Delete this contact?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            contactManager.deleteContact(cm);
                        }
                    } else {
                        // Non-button click: navigate to Emergency panel
                        cardLayout.show(contentPanel, "Emergency");
                        statusLabel.setText("Ready to send SOS to all saved contacts");
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(contactsList);
        scrollPane.setBorder(BorderFactory.createLineBorder(AppStyles.ACCENT_COLOR));

        // Add Contact Panel
        JPanel addContactPanel = new JPanel(new GridBagLayout());
        addContactPanel.setBackground(Color.WHITE);
        addContactPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            new EmptyBorder(15, 15, 15, 15)
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        JTextField nameField = new JTextField(15);
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JTextField phoneField = new JTextField(15);
        phoneField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JLabel phoneHint = new JLabel("(Include country code, e.g., +919876543210)");
        phoneHint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        phoneHint.setForeground(new Color(120, 120, 120));
        
        JButton addButton = new JButton("Add Contact");
        addButton.setBackground(new Color(40, 167, 69));
        addButton.setForeground(Color.WHITE);
        addButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        addButton.setFocusPainted(false);
        addButton.setBorder(new EmptyBorder(10, 16, 10, 16));

        gbc.gridx = 0; gbc.gridy = 0;
        addContactPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        addContactPanel.add(nameField, gbc);
        gbc.gridx = 0; gbc.gridy = 1;
        addContactPanel.add(new JLabel("Phone:"), gbc);
        gbc.gridx = 1;
        addContactPanel.add(phoneField, gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.gridwidth = 1;
        addContactPanel.add(phoneHint, gbc);
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        addContactPanel.add(addButton, gbc);

        addButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String name = nameField.getText().trim();
                String phone = phoneField.getText().trim();
                if (!name.isEmpty() && !phone.isEmpty()) {
                    contactManager.addContact(name, phone);
                    nameField.setText("");
                    phoneField.setText("");
                }
            }
        });

        // Layout
        JPanel northPanel = new JPanel(new BorderLayout(0, 15));
        northPanel.setBackground(new Color(245, 245, 250));
        northPanel.add(titleLabel, BorderLayout.NORTH);
        northPanel.add(addContactPanel, BorderLayout.CENTER);

        panel.add(northPanel, BorderLayout.NORTH);

        // Create split panel for list and buttons
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBackground(new Color(245, 245, 250));
        listPanel.add(buttonsPanel, BorderLayout.NORTH);
        listPanel.add(scrollPane, BorderLayout.CENTER);
        
        panel.add(listPanel, BorderLayout.CENTER);

        panels.put("Contacts", panel);
        contentPanel.add(panel, "Contacts");
    }

    private void showEditContactDialog(ContactModel contact) {
        JTextField nameField = new JTextField(contact.getName(), 15);
        JTextField phoneField = new JTextField(contact.getPhoneNumber(), 15);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; panel.add(nameField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Phone:"), gbc);
        gbc.gridx = 1; panel.add(phoneField, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Contact", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String newName = nameField.getText().trim();
            String newPhone = phoneField.getText().trim();
            if (!newName.isEmpty() && !newPhone.isEmpty()) {
                contactManager.editContact(contact, newName, newPhone);
            }
        }
    }

    // --- Utility and UI Setup Methods ---
    private void loadAndApplyCustomFont() {
        try {
            // Set modern fonts for the entire application
            UIManager.put("Label.font", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("Button.font", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("TextArea.font", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("CheckBox.font", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("ComboBox.font", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("List.font", new Font("Segoe UI", Font.PLAIN, 13));
        } catch (Exception e) {
            // Fallback to default if Segoe UI is not available
        }
    }
    private void applyModernStyle() {
        // Set modern look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default if system look and feel fails
        }
    }
    private void createSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(new Color(245, 245, 250));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Settings", SwingConstants.CENTER);
        titleLabel.setFont(AppStyles.getAppFont(Font.BOLD, 26f));
        titleLabel.setForeground(new Color(220, 53, 69));

        // Settings Panel
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBackground(Color.WHITE);
        settingsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            new EmptyBorder(20, 20, 20, 20)
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Default Message Setting
        JLabel messageLabel = new JLabel("Default Emergency Message:");
        messageLabel.setFont(AppStyles.getAppFont(Font.BOLD, 14f));
        messageLabel.setForeground(new Color(60, 60, 60));
        JTextField messageField = new JTextField(defaultMessage);
        messageField.setFont(AppStyles.getAppFont(Font.PLAIN, 14f));
        messageField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            new EmptyBorder(8, 10, 8, 10)
        ));

        // Auto Location Setting
        JLabel locationLabel = new JLabel("Auto-detect Location:");
        locationLabel.setFont(AppStyles.getAppFont(Font.BOLD, 14f));
        locationLabel.setForeground(new Color(60, 60, 60));
        JCheckBox locationCheck = new JCheckBox("", autoLocation);
        locationCheck.setBackground(Color.WHITE);

        // Save Button
        JButton saveButton = new JButton("Save Settings");
        saveButton.setBackground(Color.WHITE);
        saveButton.setForeground(Color.BLACK);
        saveButton.setFont(AppStyles.getAppFont(Font.BOLD, 14f));
        saveButton.setFocusPainted(false);
        saveButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            new EmptyBorder(10, 20, 10, 20)
        ));

        // Add components
        gbc.gridx = 0; gbc.gridy = 0;
        settingsPanel.add(messageLabel, gbc);
        gbc.gridy = 1;
        settingsPanel.add(messageField, gbc);

        gbc.gridy = 2;
        settingsPanel.add(locationLabel, gbc);
        gbc.gridy = 3;
        settingsPanel.add(locationCheck, gbc);

        gbc.gridy = 4;
        gbc.insets = new Insets(20, 10, 10, 10);
        settingsPanel.add(saveButton, gbc);

        saveButton.addActionListener(e -> {
            defaultMessage = messageField.getText();
            autoLocation = locationCheck.isSelected();
            JOptionPane.showMessageDialog(Main.this, "Settings saved successfully!", "Settings", JOptionPane.INFORMATION_MESSAGE);
        });

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(settingsPanel, BorderLayout.CENTER);

        panels.put("Settings", panel);
        contentPanel.add(panel, "Settings");
    }
    private void createHelpPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(new Color(245, 245, 250));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Help & Information", SwingConstants.CENTER);
        titleLabel.setFont(AppStyles.getAppFont(Font.BOLD, 26f));
        titleLabel.setForeground(new Color(220, 53, 69));

        // Help Content
        JTextPane helpText = new JTextPane();
        helpText.setEditable(false);
        helpText.setContentType("text/html");
        helpText.setText(
            "<html><body style='font-family: Segoe UI; font-size: 14px; padding: 10px;'>" +
            "<h2>How to Use the Emergency Assistant</h2>" +
            "<p><b>Sending an SOS:</b><br>" +
            "1. Save your emergency contacts in the Contacts tab (with country code, e.g., +919876543210)<br>" +
            "2. Click the SOS button on the Emergency tab<br>" +
            "3. WhatsApp Web will open separate tabs for each contact with pre-filled message<br>" +
            "4. <b style='color: #dc3545;'>IMPORTANT: You must manually click 'Send' in each WhatsApp tab</b></p>" +
            "<p><b>Managing Contacts:</b><br>" +
            "1. Go to the Contacts tab<br>" +
            "2. Add emergency contacts with their names and phone numbers<br>" +
            "3. Click Edit to modify a contact's details<br>" +
            "4. Click Delete to remove a contact</p>" +
            "<p><b>Settings:</b><br>" +
            "• Customize your default emergency message<br>" +
            "• Toggle automatic location detection (IP-based, may not be accurate)</p>" +
            "<p><b style='color: #dc3545;'>⚠️ Important Limitations:</b><br>" +
            "• <b>Manual Send Required:</b> WhatsApp blocks automatic message sending for security. You must click Send for each contact.<br>" +
            "• <b>Location Accuracy:</b> Location is detected via IP address and may be off by several kilometers. Not accurate enough for precise emergencies.<br>" +
            "• <b>Multiple Tabs:</b> One browser tab opens per contact, which can be overwhelming with many contacts.<br>" +
            "• <b>Desktop Only:</b> This is a desktop app. For real emergency use with automatic SMS and GPS, consider a mobile app.</p>" +
            "<p><b>Recommendations:</b><br>" +
            "• Always test the system with family members first<br>" +
            "• Keep 3-5 most important emergency contacts (to reduce manual effort)<br>" +
            "• Verify your location before an emergency occurs<br>" +
            "• For real emergencies, always call official emergency services (911, 112, etc.)</p>" +
            "</body></html>"
        );

        JScrollPane scrollPane = new JScrollPane(helpText);
        scrollPane.setBorder(BorderFactory.createLineBorder(AppStyles.ACCENT_COLOR));

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        panels.put("Help", panel);
        contentPanel.add(panel, "Help");
    }
    private void createProfilePanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(new Color(245, 245, 250));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel("Profile", SwingConstants.CENTER);
        titleLabel.setFont(AppStyles.getAppFont(Font.BOLD, 26f));
        titleLabel.setForeground(new Color(220, 53, 69));

        JPanel info = new JPanel(new GridLayout(0, 1, 10, 10));
        info.setBackground(Color.WHITE);
        info.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel fullName = new JLabel("Full Name: ");
        JLabel idType = new JLabel("ID Type: ");
        JLabel idNumber = new JLabel("ID Number: ");
        JLabel email = new JLabel("Email: ");
        JLabel phone = new JLabel("Phone: ");

        fullName.setFont(AppStyles.getAppFont(Font.BOLD, 16f));
        fullName.setForeground(new Color(50, 50, 50));
        idType.setFont(AppStyles.getAppFont(Font.PLAIN, 14f));
        idType.setForeground(new Color(80, 80, 80));
        idNumber.setFont(AppStyles.getAppFont(Font.PLAIN, 14f));
        idNumber.setForeground(new Color(80, 80, 80));
        email.setFont(AppStyles.getAppFont(Font.PLAIN, 14f));
        email.setForeground(new Color(80, 80, 80));
        phone.setFont(AppStyles.getAppFont(Font.PLAIN, 14f));
        phone.setForeground(new Color(80, 80, 80));

        info.add(fullName);
        info.add(idType);
        info.add(idNumber);
        info.add(email);
        info.add(phone);

        // Logout button
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setBackground(Color.WHITE);
        logoutBtn.setForeground(Color.BLACK);
        logoutBtn.setFont(AppStyles.getAppFont(Font.BOLD, 14f));
        logoutBtn.setFocusPainted(false);
        logoutBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            new EmptyBorder(10, 20, 10, 20)
        ));
        logoutBtn.addActionListener(e -> {
            // Clear session and return to auth dialog
            userManager.clearSession();
            currentUser = null;
            // Reset contact manager to an empty model until login
            contactManager = new ContactManager(database, "");
            contactsList.setModel(contactManager.getContactsListModel());
            showAuthDialog();
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setBackground(new Color(245, 245, 250));
        south.setBorder(new EmptyBorder(15, 0, 0, 0));
        south.add(logoutBtn);

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(info, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);

        // Update with current user data if available
        if (currentUser != null) {
            fullName.setText("Full Name: " + currentUser.fullName);
            idType.setText("ID Type: " + currentUser.idType);
            idNumber.setText("ID Number: " + currentUser.idNumber);
            email.setText("Email: " + currentUser.email);
            phone.setText("Phone: " + currentUser.phone);
        }

        panels.put("Profile", panel);
        contentPanel.add(panel, "Profile");
    }
    private void showAuthDialog() {
        JDialog dlg = new JDialog(this, "Welcome", true);
        dlg.setSize(400, 300);
        dlg.setLocationRelativeTo(this);
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(new EmptyBorder(10,10,10,10));
        dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dlg.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { System.exit(0); }
        });

        JLabel welcome = new JLabel("Please login or register", SwingConstants.CENTER);
        welcome.setFont(AppStyles.getAppFont(Font.BOLD, 18f));
        p.add(welcome, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout());
        JButton loginBtn = new JButton("Login");
        loginBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        JButton regBtn = new JButton("Register");
        regBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        buttons.add(loginBtn);
        buttons.add(regBtn);
        p.add(buttons, BorderLayout.CENTER);

        loginBtn.addActionListener(e -> {
            dlg.dispose();
            showLoginDialog();
        });
        regBtn.addActionListener(e -> {
            dlg.dispose();
            showRegisterDialog();
        });

        dlg.setContentPane(p);
        dlg.setVisible(true);
    }

    private void showLoginDialog() {
        JDialog dlg = new JDialog(this, "Login", true);
        dlg.setSize(480, 300);
        dlg.setLocationRelativeTo(this);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(10,10,10,10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8,8,8,8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dlg.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { System.exit(0); }
        });

        JTextField emailField = new JTextField(22);
        emailField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JPasswordField passField = new JPasswordField(22);
        passField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JButton backBtn = new JButton("Back");
        backBtn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JButton loginBtn = new JButton("Login");
        loginBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));

        gbc.gridx = 0; gbc.gridy = 0; p.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1; p.add(emailField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; p.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; p.add(passField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; p.add(backBtn, gbc);
        gbc.gridx = 1; p.add(loginBtn, gbc);

        backBtn.addActionListener(e -> {
            dlg.dispose();
            showAuthDialog();
        });

        loginBtn.addActionListener(e -> {
            String email = emailField.getText().trim();
            String pass = new String(passField.getPassword());
            if (userManager.login(email, pass)) {
                currentUser = userManager.getUser(email);
                contactManager = new ContactManager(database, currentUser.email);
                contactsList.setModel(contactManager.getContactsListModel());
                updateProfilePanel();
                dlg.dispose();
            } else {
                String detail = userManager.getLastError();
                if (detail == null || detail.isBlank()) detail = "Invalid credentials.";
                JOptionPane.showMessageDialog(Main.this, "Login failed: " + detail, "Login failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        dlg.setContentPane(p);
        dlg.setVisible(true);
    }

    private void showRegisterDialog() {
        JDialog dlg = new JDialog(this, "Register", true);
        dlg.setSize(560, 480);
        dlg.setLocationRelativeTo(this);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(10,10,10,10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dlg.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { System.exit(0); }
        });

        String[] idTypes = {"Aadhar","Driving License","Passport","Ration Card"};
        JComboBox<String> idTypeBox = new JComboBox<>(idTypes);
        JTextField fullNameField = new JTextField(22);
        fullNameField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JTextField idNumber = new JTextField(22);
        idNumber.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JTextField emailField = new JTextField(22);
        emailField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JTextField phoneField = new JTextField(22);
        phoneField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JLabel phoneHint = new JLabel("(Include country code, e.g., +919876543210)");
        phoneHint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        phoneHint.setForeground(new Color(120, 120, 120));
        
        JCheckBox allowLoc = new JCheckBox("Allow location permission");
        allowLoc.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JPasswordField passField = new JPasswordField(22);
        passField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JButton backBtn = new JButton("Back");
        backBtn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JButton registerBtn = new JButton("Register");
        registerBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));

        gbc.gridx = 0; gbc.gridy = 0; p.add(new JLabel("Full Name:"), gbc);
        gbc.gridx = 1; p.add(fullNameField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; p.add(new JLabel("ID Type:"), gbc);
        gbc.gridx = 1; p.add(idTypeBox, gbc);
        gbc.gridx = 0; gbc.gridy = 2; p.add(new JLabel("ID Number:"), gbc);
        gbc.gridx = 1; p.add(idNumber, gbc);
        gbc.gridx = 0; gbc.gridy = 3; p.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1; p.add(emailField, gbc);
        gbc.gridx = 0; gbc.gridy = 4; p.add(new JLabel("Phone:"), gbc);
        gbc.gridx = 1; p.add(phoneField, gbc);
        gbc.gridx = 1; gbc.gridy = 5; gbc.gridwidth = 1; p.add(phoneHint, gbc);
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; p.add(allowLoc, gbc);
        gbc.gridy = 7; gbc.gridwidth = 1; p.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; p.add(passField, gbc);
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 1; p.add(backBtn, gbc);
        gbc.gridx = 1; p.add(registerBtn, gbc);

        backBtn.addActionListener(e -> {
            dlg.dispose();
            showAuthDialog();
        });

        registerBtn.addActionListener(e -> {
            String fullName = fullNameField.getText().trim();
            String idT = (String)idTypeBox.getSelectedItem();
            String idN = idNumber.getText().trim();
            String email = emailField.getText().trim().toLowerCase();
            String phone = phoneField.getText().trim();
            String pass = new String(passField.getPassword());
            if (fullName.isEmpty() || idN.isEmpty() || email.isEmpty() || phone.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(Main.this, "Please fill all fields", "Missing data", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Check if email already exists to provide clear feedback
            UserManager.User existing = userManager.getUser(email);
            if (existing != null) {
                JOptionPane.showMessageDialog(Main.this, "This email is already registered. Please login or use another email.", "Email in use", JOptionPane.WARNING_MESSAGE);
                return;
            }
            UserManager.User u = new UserManager.User(fullName, idT, idN, email, phone, pass);
            boolean ok = userManager.register(u);
            if (!ok) {
                String detail = userManager.getLastError();
                if (detail == null || detail.isBlank()) detail = "Please try a different email or check your database connection.";
                JOptionPane.showMessageDialog(Main.this, "Registration failed: " + detail, "Register failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            currentUser = u;
            autoLocation = allowLoc.isSelected();
            contactManager = new ContactManager(database, currentUser.email);
            contactsList.setModel(contactManager.getContactsListModel());
            updateProfilePanel();
            dlg.dispose();
        });

        dlg.setContentPane(p);
        dlg.setVisible(true);
    }

    private void updateProfilePanel() {
        JPanel profile = panels.get("Profile");
        if (profile == null || currentUser == null) return;
        // replace labels in profile panel
        for (Component c : profile.getComponents()) {
            if (c instanceof JPanel) {
                for (Component cc : ((JPanel)c).getComponents()) {
                    if (cc instanceof JLabel) {
                        String txt = ((JLabel)cc).getText();
                        if (txt.startsWith("Full Name:")) ((JLabel)cc).setText("Full Name: " + currentUser.fullName);
                        if (txt.startsWith("ID Type:")) ((JLabel)cc).setText("ID Type: " + currentUser.idType);
                        if (txt.startsWith("ID Number:")) ((JLabel)cc).setText("ID Number: " + currentUser.idNumber);
                        if (txt.startsWith("Email:")) ((JLabel)cc).setText("Email: " + currentUser.email);
                        if (txt.startsWith("Phone:")) ((JLabel)cc).setText("Phone: " + currentUser.phone);
                    }
                }
            }
        }
    }
    private Font getAppFont(int style, float size) {
        return new Font("Segoe UI", style, (int)size);
    }

    /**
     * Normalize phone numbers by removing punctuation and ensuring country code present.
     * Returns digits-only phone string suitable for WhatsApp URL (e.g. 919876543210)
     * or null if invalid.
     */
    private String normalizePhone(String raw) {
        if (raw == null) return null;
        // Remove spaces, plus signs, parentheses, dashes
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        // If number looks like local (10 digits) try to assume default country code? We should not guess.
        // Require at least country code + number (minimum 11-12 digits depending on country). We'll accept 10-15 digits.
        if (digits.length() < 10 || digits.length() > 15) return null;
        // If length == 10, user likely omitted country code; return null to force user to fix
        if (digits.length() == 10) return null;
        return digits;
    }
    
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            new Main().setVisible(true);
        });
    }
}