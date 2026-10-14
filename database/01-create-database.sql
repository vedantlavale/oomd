-- ============================================================================
-- VSMS - Vehicle Service Management System: database creation script (MySQL 8)
--
-- Creates an empty database. The tables are created automatically by the
-- application on its first start (or from database/02-schema.sql if you prefer).
--
-- The application should NOT connect as "root". Create a dedicated user that can
-- only touch this one database. Choose your own password - never reuse a real one
-- and never put it in a file that is shared. The helper scripts/setup-database.ps1
-- does all of this for you and asks for the passwords interactively.
--
-- Run this script as the MySQL administrator:
--   mysql -u root -p -e "source database/01-create-database.sql"
-- ============================================================================

CREATE DATABASE IF NOT EXISTS vsms_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- Do this part yourself, in the mysql prompt, replacing <your-password>:
--
--   CREATE USER 'vsms_user'@'localhost' IDENTIFIED BY '<your-password>';
--   GRANT ALL PRIVILEGES ON vsms_db.* TO 'vsms_user'@'localhost';
--   FLUSH PRIVILEGES;
--
-- ALL PRIVILEGES on vsms_db only (not *.*) is enough for the application to create
-- and change its own tables. To use a stricter account later, create the tables with
-- database/02-schema.sql, grant only SELECT, INSERT, UPDATE, DELETE and start the
-- application with VSMS_DDL_AUTO=validate.
