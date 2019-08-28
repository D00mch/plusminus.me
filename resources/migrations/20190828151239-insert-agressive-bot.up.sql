INSERT INTO users(id, pass)
VALUES('AggressiveBot', substr(md5(random()::text), 0, 25));
