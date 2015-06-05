(ns linkoln.exercise
  (:require [clojure.java.io :as io]
            [linkoln.model :as model])
  (:use [clj-jgit porcelain querying]))

(defn start-to-solve [exercise-id username]
  ;; リポジトリをフォークする。
  (let [exercise (model/pull '[*] (Long/parseLong exercise-id))
        subject-dir (io/file "git-subject" (:exercise/name exercise))
        local-dir (io/file "git" username (:exercise/name exercise))]
    (git-clone (str (.toURL subject-dir)) (str local-dir) "origin" "master" true)))

(defn fork-exercise [name url]
  (let [local-dir (io/file "git-subject" name)]
    (git-clone url (str local-dir) "origin" "master" true)))

(defn get-readme [exercise]
  (let [repo (load-repo (io/file "git-subject" (:exercise/name exercise)))
        db (.. repo getRepository getObjectDatabase)
        rev-commit (->> (branch-list-with-heads repo)
                        (filter #(= (.getName (first %)) "refs/heads/master"))
                        first
                        second)
        object-id (get-blob-id repo rev-commit "README.md")]
    (String. (.. (.open db object-id) getBytes) "UTF-8")))
