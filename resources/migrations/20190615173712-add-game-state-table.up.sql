CREATE TABLE game_state
(user_id  VARCHAR(20) PRIMARY KEY NOT NULL,
cells    SMALLINT [],
hrz_turn BOOLEAN,
moves    SMALLINT [],
start    SMALLINT);
