CREATE TABLE users
(id         VARCHAR(20) PRIMARY KEY NOT NULL,
 pass       VARCHAR(300) NOT NULL,
 last_login TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
 email      VARCHAR(30),
 admin      BOOLEAN,
 is_active  BOOLEAN,
 features   JSON);
--;;
CREATE INDEX ON users USING hash(email);
