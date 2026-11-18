-- FINORA local development + test databases (run once per machine as MySQL root).
-- The finora/finora account is for LOCAL use only. Never use it on the server.
CREATE DATABASE IF NOT EXISTS finora      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS finora_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'finora'@'localhost' IDENTIFIED BY 'finora';
GRANT ALL PRIVILEGES ON finora.*      TO 'finora'@'localhost';
GRANT ALL PRIVILEGES ON finora_test.* TO 'finora'@'localhost';
FLUSH PRIVILEGES;
