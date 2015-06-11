(ns linkoln.score
  (:use [clojure.java.shell :only [with-sh-dir sh]]))

(defprotocol Scoring
  (calculate [workspace] "calculate a unidimensional score")
  (issues [workspace]))

(deftype SonarScorer []
  Scoring
  (calculate [workspace]
    (with-sh-dir workspace
      (sh "mvn" "sonar:sonar" "-Dsonar.analysis.mode=preview" "-Dsonar.issuesReport.html.enable=true")))
  (issues [workspace]))

