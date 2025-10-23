# Save Our Ship (SOS)

A Java Swing desktop application for emergency situations with one-click SOS alerts via WhatsApp, contact management, secure authentication, and location tracking.

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-11%2B-orange.svg)
![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-blue.svg)

## 🌟 Features

- **🆘 One-Click SOS**: Send emergency alerts to all saved contacts instantly
- **💬 WhatsApp Integration**: Automatic message sending via WhatsApp Desktop/Web
- **📍 Location Tracking**: Browser-based geolocation with IP fallback for accurate coordinates
- **👥 Contact Management**: Add, edit, and delete emergency contacts with phone validation
- **🔐 Secure Authentication**: PBKDF2WithHmacSHA256 password hashing with session persistence
- **💾 MySQL Database**: Persistent storage for users, contacts, and location history
- **🎨 Modern UI**: Clean Segoe UI interface with emergency-themed styling
- **⚙️ Customizable Settings**: Edit default emergency message template and location preferences

## 🛠️ Tech Stack

- **Language**: Java 11+
- **UI Framework**: Swing (javax.swing)
- **Database**: MySQL 8.0+
- **JDBC Driver**: MySQL Connector/J 9.4.0
- **Security**: PBKDF2WithHmacSHA256 password hashing with salted storage
- **External APIs**: 
  - WhatsApp Web API (whatsapp:// protocol & web.whatsapp.com)
  - IP Geolocation API (ip-api.com)
  - Browser Geolocation API (navigator.geolocation)

## 📋 Prerequisites

Before running the application, ensure you have:

- **Java JDK 11 or higher** - [Download here](https://www.oracle.com/java/technologies/downloads/)
- **MySQL Server 8.0+** - [Download here](https://dev.mysql.com/downloads/mysql/)
- **WhatsApp Desktop** (optional but recommended) - [Download here](https://www.whatsapp.com/download)
- A web browser (Chrome, Edge, Firefox) for location permissions

## 🚀 Installation & Setup

### 1. Clone or Download the Repository

```bash
git clone https://github.com/YOUR_USERNAME/emergency-assistance-hub.git
cd emergency-assistance-hub
```

### 2. Database Setup

Create the database and tables using the provided schema:

```bash
# Login to MySQL
mysql -u root -p

# Create database
CREATE DATABASE sosdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sosdb;

# Run schema script
source schema.sql;

# Or on Windows with MySQL command line:
# mysql -u root -p sosdb < schema.sql
```

### 3. Configure Database Connection

Create a `db.properties` file in the project root:

```properties
db.url=jdbc:mysql://localhost:3306/sosdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.user=root
db.password=YOUR_MYSQL_PASSWORD
```

**⚠️ Important**: Never commit `db.properties` to version control (already in `.gitignore`)

### 4. Compile and Run

#### Windows (using batch file):
```bash
run.bat
```

#### Manual compilation:
```bash
# Compile
javac -cp ".;mysql-connector-j-9.4.0.jar" -d out *.java

# Run
java -cp ".;out;mysql-connector-j-9.4.0.jar" Main
```

#### Linux/Mac:
```bash
# Compile
javac -cp ".:mysql-connector-j-9.4.0.jar" -d out *.java

# Run
java -cp ".:out:mysql-connector-j-9.4.0.jar" Main
```

## 📱 Usage Guide

### First Time Setup

1. **Launch the application** - You'll see a welcome dialog
2. **Register** with your details:
   - Full Name
   - ID Type (Aadhar, Driving License, Passport, Ration Card)
   - ID Number
   - Email (used for login)
   - Phone number with country code (e.g., +919876543210)
   - Password
   - Allow location permission (recommended)

3. **Login** on subsequent launches with your email and password

### Adding Emergency Contacts

1. Navigate to the **Contacts** tab
2. Enter contact name and phone number
   - ⚠️ **Important**: Include country code (e.g., +919876543210, +14155551234)
3. Click **Add Contact**
4. Use **Edit** to modify contact details
5. Use **Delete** to remove contacts

### Sending an SOS Alert

1. Navigate to the **Emergency** tab
2. Click the red **SOS** button
3. Confirm the action in the dialog
4. The app will:
   - Detect your location (browser permission required)
   - Open WhatsApp Desktop/Web for each contact
   - Pre-fill emergency message with your name and location
5. **Manually click "Send"** in each WhatsApp tab (platform limitation)

### Customizing Settings

1. Navigate to the **Settings** tab
2. Edit the default emergency message template
   - Use `<name>` placeholder to insert your full name
   - Example: `"Hello, EMERGENCY... I am <name>. I need help... My location:"`
3. Toggle auto-location detection
4. Click **Save Settings**

## 🔒 Security Features

- **Password Hashing**: PBKDF2WithHmacSHA256 with 65,536 iterations and random salt
- **Session Persistence**: Encrypted session files for "remember me" functionality
- **SQL Injection Protection**: Prepared statements for all database queries
- **Sensitive Data**: `db.properties` and session files excluded from version control

## ⚠️ Important Limitations

### Known Constraints:

1. **Manual Send Required**: WhatsApp blocks automatic message sending for security. You must manually click "Send" for each contact.

2. **Location Accuracy**: 
   - Browser geolocation (requires permission) is more accurate
   - IP-based fallback may be off by several kilometers
   - Not accurate enough for precise emergency services

3. **Multiple Tabs**: One browser tab opens per contact, which can be overwhelming with many contacts (keep 3-5 key contacts).

4. **Desktop Only**: This is a desktop app. For real emergency use with automatic SMS and GPS:
   - Consider migrating to Android/iOS
   - Use native mobile emergency features

5. **Phone Format**: Phone numbers **must** include country code (no automatic prepending).

## 📂 Project Structure

```
emergency-assistance-hub/
├── Main.java                    # Main UI and application logic
├── ContactManager.java          # Contact CRUD operations
├── ContactModel.java            # Contact data model
├── UserManager.java             # Authentication and session management
├── Database.java                # Database connection and queries
├── PasswordUtil.java            # Password hashing utilities
├── AppStyles.java               # UI theme and styling constants
├── UIComponents.java            # Reusable UI components
├── schema.sql                   # Database schema
├── run.bat                      # Windows run script
├── run.ps1                      # PowerShell run script
├── mysql-connector-j-9.4.0.jar  # JDBC driver
├── .gitignore                   # Git ignore rules
├── LICENSE                      # MIT License
└── README.md                    # This file
```

## 🐛 Troubleshooting

### Database Connection Errors
- Verify MySQL is running: `mysql -u root -p`
- Check `db.properties` credentials
- Ensure database `sosdb` exists
- Check firewall/port 3306 is open

### WhatsApp Not Opening
- Install WhatsApp Desktop for better integration
- Check if browser blocks popups
- Verify phone numbers include country code

### Location Not Working
- Allow browser location permission when prompted
- Check internet connection (IP fallback requires it)
- Try a different browser if geolocation fails

### Compilation Errors
- Verify Java JDK 11+ is installed: `java -version`
- Ensure `mysql-connector-j-9.4.0.jar` is in the project directory
- Check classpath separator (`;` on Windows, `:` on Linux/Mac)

## 🤝 Contributing

Contributions are welcome! Please feel free to:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built as an academic project for emergency assistance
- WhatsApp Web API for messaging integration
- IP Geolocation by [ip-api.com](https://ip-api.com/)
- MySQL for database management
- Java Swing for cross-platform UI

## 📞 Support

For questions, issues, or suggestions:
- Open an issue in this repository
- Contact: [Your Email]

---

**⚠️ Disclaimer**: This application is intended for educational and supplementary emergency use only. In a real emergency, always contact official emergency services (911, 112, etc.) first. The developers are not liable for any damages or injuries resulting from the use of this software.

---

Made with ❤️ for safety and emergency preparedness
