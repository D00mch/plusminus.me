CREATE TABLE online_statistics (
  player_id  VARCHAR(20) PRIMARY KEY NOT NULL,
  iq         SMALLINT,
  statistics JSON
);
