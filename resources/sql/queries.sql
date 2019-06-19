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
INSERT INTO game_states
(player_id, state)
VALUES (:id, :state)
ON CONFLICT (player_id)
DO UPDATE
SET state = :state

-- :name get-state :? :1
-- :doc retrieves game state given the id
SELECT * FROM game_states
WHERE player_id = :id


-- :name upsert-statistics! :! :n
-- :doc create or update statistics given user id
INSERT INTO game_statistics
(player_id, statistics)
VALUES (:id, :statistics)
ON CONFLICT (player_id)
DO UPDATE
SET statistics = :statistics

-- :name get-statistics :? :1
-- :doc retrieves game statistics given the id
SELECT * FROM game_statistics
WHERE player_id = :id
