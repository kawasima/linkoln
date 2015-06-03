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
            [url :uri]
            [answers :ref :many]))
   (schema answer
           (fields
            [student :ref]
            [status :enum [:started :submitted]]
            [score :float]))
   (schema student
           (fields
            [name :string :unique-value]))])

(defn create-schema []
  (d/create-database uri)
  (reset! conn (d/connect uri))
  (let [schema (concat
                (s/generate-parts (dbparts))
                (s/generate-schema (dbschema)))]
    (d/transact @conn schema)))
