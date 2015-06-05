(ns linkoln.style
  (:use [garden.core :only [css]]
        [garden.units :only [px em]]))

(def styles
  [[:.main.menu {:box-shadow "0 0 10px rgba(0,0,0,0.3)"}
    [:.title.item {:font-size (px 18)
                   :font-weight "bold"
                   :font-variant "small-caps"}]]
   [:.wrapper {:margin "0em auto"
               :padding "4em 2em 7em"
               :width (px 960)
               :min-height "100%"}
    [:.full.height {:flex 1}]]
   [:.container {:width (px 960)
                 :margin "0em auto"}]
   [:.login-screen
    [:.ui.form {:background-color "#ecf0f1"
                :width (px 400)
                :margin {:right "auto"
                         :left  "auto"}}
     [:input {:margin {:bottom (em 1)}}]]]])

(defn build []
  (css {:pretty-print? false} styles))
