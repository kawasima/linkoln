(ns linkoln.exercise
  (:require [clojure.java.io :as io]
            [linkoln.model :as model])
  (:use [clj-jgit porcelain querying])
  (:import [org.pegdown PegDownProcessor Extensions]))

(def pegdown (PegDownProcessor. Extensions/ALL))

(defn start-to-solve [exercise-id username]
  (let [exercise (model/pull '[*] (Long/parseLong exercise-id))
        subject-dir (io/file "git-subject" (:exercise/name exercise))
        local-dir (io/file "git" username (:exercise/name exercise))]
    (git-clone (str (.toURL subject-dir)) (str local-dir) "origin" "master" true)))

(defn fork-exercise [name url]
  (let [local-dir (io/file "git-subject" name)]
    (git-clone url (str local-dir) "origin" "master" true)))

(defn clone-to-workspace [exercise-id username]
  (let [exercise (model/pull '[*] (Long/parseLong exercise-id))
        fork-dir (io/file "git" username (:exercise/name exercise))
        local-dir (io/file "workspace" username (:exercise/name exercise))]
    (git-clone (str (.toURL fork-dir)) (str local-dir) "origin" "master" false)))

(defn get-readme [exercise]
  (let [repo (load-repo (io/file "git-subject" (:exercise/name exercise)))
        db (.. repo getRepository getObjectDatabase)
        rev-commit (->> (branch-list-with-heads repo)
                        (filter #(= (.getName (first %)) "refs/heads/master"))
                        first
                        second)
        object-id (get-blob-id repo rev-commit "README.md")
        markdown (String. (.. (.open db object-id) getBytes) "UTF-8")]
    (.markdownToHtml pegdown markdown)))
