CREATE TABLE game_states (
 player_id VARCHAR(20) PRIMARY KEY NOT NULL,
 state     JSON
);
--;;
CREATE TABLE game_statistics (
 player_id  VARCHAR(20) PRIMARY KEY NOT NULL,
 statistics JSON
);
