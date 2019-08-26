CREATE TABLE game_states (
 player_id VARCHAR(20) PRIMARY KEY NOT NULL,
 state     JSON,
 FOREIGN KEY (player_id) REFERENCES users(id) ON DELETE CASCADE
);
--;;
CREATE TABLE game_statistics (
 player_id  VARCHAR(20) PRIMARY KEY NOT NULL,
 statistics JSON,
 FOREIGN KEY (player_id) REFERENCES users(id) ON DELETE CASCADE
);
