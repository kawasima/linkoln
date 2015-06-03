(ns linkoln.exercise
  (:require [clojure.java.io :as io]
            [linkoln.model :as model])
  (:use [clj-jgit porcelain]))

(defn start-to-solve [exercise-id username]
  ;; リポジトリをフォークする。
  (let [exercise (model/pull '[*] (Long/parseLong exercise-id))
        local-dir (io/file "git" username (:exercise/name exercise))]
    (git-clone (str (:exercise/url exercise)) (str local-dir) "origin" "master" true)))

;; Submit exercise
(defn end-to-solve []
  ;; Push hook
  )

