-- :name create-user! :! :n
-- :doc creates a new user record
INSERT INTO users
(id, pass)
VALUES (:id, :pass)

-- :name update-user! :! :n
-- :doc updates an existing user record
UPDATE users
SET pass = :pass, email = :email
WHERE id = :id

-- :name get-user :? :1
-- :doc retrieves a user record given the id
SELECT * FROM users
WHERE id = :id

-- :name delete-user! :! :n
-- :doc deletes a user record given the id
DELETE FROM users
WHERE id = :id


-- :name upsert-state! :! :n
-- :doc create or update game-state by user id
INSERT INTO game_state
(user_id, cells, hrz_turn, moves, start)
VALUES (:id, :cells, :hrz-turn, :moves, :start)
ON CONFLICT (user_id)
DO UPDATE
SET cells = :cells, hrz_turn = :hrz-turn, moves = :moves, start = :start

-- :name get-state :? :1
-- :doc retrieves game state given the id
SELECT * FROM game_state
WHERE user_id = :id

