(ns linkoln.model
  (:use [datomic-schema.schema :only [fields part schema]])
  (:require [datomic.api :as d]
            [datomic-schema.schema :as s]))

(def uri "datomic:mem://lincoln")

(defonce conn (atom nil))

(defn query [q & params]
  (let  [db (d/db @conn)]
    (apply d/q q db params)))

(defn pull [pattern eid]
  (let [db (d/db @conn)]
    (d/pull db pattern eid)))

(defn transact [transaction]
  @(d/transact @conn transaction))

(defn dbparts []
  [(part "linkoln")])

(defn dbschema []
  [(schema exercise
           (fields
            [name :string :indexed :unique-value :fulltext]
            [description :string]
            [url :uri]
            [answers :ref :many]))
   (schema answer
           (fields
            [user :ref]
            [status :enum [:started :submitted]]
            [scorings :ref :many]))
   (schema scoring
           (fields
            [:score :float]
            [:comprehensive-evaluation :string]
            [:comments :string :many]))
   (schema user
           (fields
            [name :string :unique-value]
            [password :string]
            [salt :bytes]
            [role :enum [:student :teacher]]))])

(defn create-schema []
  (d/create-database uri)
  (reset! conn (d/connect uri))
  (let [schema (concat
                (s/generate-parts (dbparts))
                (s/generate-schema (dbschema)))]
    (d/transact @conn schema)))
