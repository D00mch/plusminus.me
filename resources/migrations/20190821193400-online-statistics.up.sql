CREATE TABLE online_statistics (
  id         VARCHAR(20) PRIMARY KEY NOT NULL,
  iq         SMALLINT,
  statistics JSONB,
  FOREIGN KEY (id) REFERENCES users(id) ON DELETE CASCADE
);
