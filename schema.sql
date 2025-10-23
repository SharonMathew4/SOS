-- Create database and tables for SOS app
-- Run in MySQL (adjust DB name/charset as needed)

-- CREATE DATABASE IF NOT EXISTS sosdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE sosdb;

-- Users table: store password as a hash (PBKDF2/bcrypt string)
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(100) NOT NULL,
  id_type VARCHAR(50) NOT NULL,
  id_number VARCHAR(100) NOT NULL,
  email VARCHAR(255) NOT NULL,
  phone VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  allow_location TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_email (email)
);

-- Contacts table: per-user contacts
CREATE TABLE IF NOT EXISTS contacts (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(50) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_contacts_user (user_id),
  CONSTRAINT fk_contacts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Location history
CREATE TABLE IF NOT EXISTS locations (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_locations_user_time (user_id, recorded_at),
  CONSTRAINT fk_locations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- If you already created the older schema with a 'password' column,
-- you can migrate with:
-- ALTER TABLE users CHANGE COLUMN password password_hash VARCHAR(255) NOT NULL;
-- If you created the users table without full_name, add it with:
-- ALTER TABLE users ADD COLUMN full_name VARCHAR(100) NOT NULL AFTER id;
