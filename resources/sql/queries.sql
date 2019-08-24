-- :name create-user! :! :n
-- :doc creates a new user record
INSERT INTO users
(id, pass)
VALUES (:id, :pass)

-- :name update-user! :! :n
-- :doc updates an existing user record
UPDATE users SET
--~ (if (contains? params :pass) "pass = :pass" "pass = pass")
--~ (when (contains? params :email) ",email = :email")
--~ (when (contains? params :last_login) ",last_login = :last_login")
--~ (when (contains? params :features) ",features = :features")
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


-- :name upsert-online-stats! :! :n
-- :doc create or update online statistics given user id
INSERT INTO online_statistics
(id, iq, statistics)
VALUES (:id, :iq, :statistics)
ON CONFLICT (id)
DO UPDATE
SET statistics = :statistics, iq = :iq

-- :name get-online-stats :? :1
-- :doc retrieves online game statistics given the id
SELECT * FROM online_statistics
WHERE id = :id

-- :name get-all-online-stats :? :*
-- :doc retrieves all online statistics records, ordered by iq
SELECT * FROM online_statistics
ORDER BY iq DESC
