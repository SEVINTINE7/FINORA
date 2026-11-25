CREATE TABLE admin_bootstrap_lock (
    id TINYINT PRIMARY KEY CHECK (id = 1)
);
INSERT INTO admin_bootstrap_lock (id) VALUES (1);
