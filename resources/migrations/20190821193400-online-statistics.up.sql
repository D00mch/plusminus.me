CREATE TABLE online_statistics (
  id         VARCHAR(20) PRIMARY KEY NOT NULL,
  iq         SMALLINT,
  statistics JSON
);
