(ns linkoln.admin
  (:use [compojure.core :only [defroutes GET POST]]
        [ring.util.anti-forgery :only [anti-forgery-field]]
        [ring.util.response :only [redirect response content-type]]
        [hiccup.core :only [html]]
        [hiccup.page :only [html5 include-css include-js]])
  (:require [buddy.core.nonce :as nonce]
            (linkoln [model :as model]
                     [exercise :as exercise]
                     [style :as style]))
  (:import [java.net URI]))

(defn- layout-admin [& contents]
  (html5
   [:head
    [:meta {:name "viewport" :content "width=device=width, initial-scale=1"}]
    (include-js "https://code.jquery.com/jquery-1.11.3.min.js"
                "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/1.12.3/semantic.min.js")
    (include-css "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/1.12.3/semantic.min.css"
                 "/css/linkoln.css")
    [:title "Linkoln"]]
   [:body
    [:div.ui.fixed.main.menu
     [:div.container
      [:a.item.title
       [:i.green.student.icon]
       "Linkoln"]
      [:div.right.menu
      [:a.ui.item {:href "/logout"} "Logout"]]]]
    [:div.wrapper
     [:div.full.height contents]]]))

(defn- list-exercise [exercises {{role :role} :session username :identity :as req}]
  (layout-admin
   [:h2.ui.header
    [:i.user.icon]
    [:div.content "Courses"]]
   [:table.ui.celled.table
    [:thead
     [:tr
      [:th {:colspan 3} "Exercises"]]]
    [:tbody
     (for [exercise exercises]
       (let [status (get-in exercise [:answer/status :db/ident])]
         [:tr
          [:td [:a {:onclick (str "$('#exercise-modal-" (:db/id exercise) "').modal('show');") }
                (:exercise/name exercise)]]
          [:td (case status
                 :answer.status/started
                 [:div
                  (str (name (:scheme req)) "://" (:server-name req) ":" (:server-port req) "/git/" (name username) "/" (:exercise/name exercise) ".git")]
                 :answer.status/submitted
                 [:div
                  "submitted"]
                 (:exercise/description exercise))]
          [:td (:exercise/answers exercise)]]))]]
   (when (= role :user.role/teacher)
     [:a.ui.primary.button {:href "/admin/exercises/new"} "New exercise"])
   [:div.ui.dimmer.modals.page.transition.hidden
    (for [exercise exercises]
      [:div.ui.modal.transition.hidden {:id (str "exercise-modal-" (:db/id exercise))}
       [:div.content (exercise/get-readme exercise)]])]))

(defn- new-exercise [req]
  (layout-admin
   [:form.ui.form {:action "/admin/exercises/new" :method "POST"}
    (anti-forgery-field)
    [:div.field
     [:label "Name"]
     [:input {:type "text" :name "name" :placeholder "Excercise name"}]]
    [:div.field
     [:label "URL"]
     [:input {:type "text" :name "url" :placeholder ""}]]
    [:div.field
     [:label "Description"]
     [:textarea {:name "description"}]]
    [:button.ui.positive.button {:type "submit"} "Save"]]))

(defn- list-users [users]
  (layout-admin
   [:h2.ui.header
    [:i.users.icon]
    [:div.content "Users"]]
   [:div.right.aligned
    [:a.ui.button {:href "/admin/users/new"} "New"]]
   [:table.ui.celled.table
    [:thead
     [:tr
      [:th "Name"]
      [:th "Role"]]]
    [:tbody
     (for [user users]
       [:tr
        [:td (:user/name user)]
        [:td (get-in user [:user/role :db/ident])]])]]))

(defn- new-user [req]
  (layout-admin
   [:form.ui.form {:action "/admin/users/new" :method "POST"}
    (anti-forgery-field)
    [:div.two.fields
     [:div.field
      [:label "Username"]
      [:input {:type "text" :name "name" :placeholder "Username"}]]
     [:div.field
      [:label "Password"]
      [:input {:type "password" :name "password" :placeholder "Password"}]]]
    [:div.field
     [:label "Role"]
     [:select {:name "role"}
      [:option {:value "student"} "student"]
      [:option {:value "teacher"} "teacher"]]]
    [:button.ui.positive.button {:type "submit"} "Save"]]))
;
(defroutes admin-routes
  (GET "/exercises/" req
    (let [exercises (model/query '{:find [[?exercise ...]]
                                   :where [[?exercise :exercise/name]]})]
      (list-exercise (->> exercises
                          (map #(model/pull '[:*] %))
                          (map #(update-in % [:exercise/answers] count)))
                     req)))

  
  (GET "/exercises/new" req (new-exercise req))
  (POST "/exercises/new" {{:keys [name url description]} :params :as req}
    (exercise/fork-exercise name url)
    (model/transact [{:db/id #db/id[db.part/user -1]
                      :exercise/name name
                      :exercise/description description
                      :exercise/url  (URI/create url)}])
    (redirect "/admin/exercises/"))
  (GET "/users/" req
    (let [users (model/query '{:find [[(pull ?u [:* {:user/role [:db/ident]}]) ...]]
                               :where [[?u :user/name]]})]
      (list-users users)))
  (GET "/users/new" req (new-user req))
  (POST "/users/new" {{:keys [name password role]} :params :as req}
    (let [salt (nonce/random-nonce 16)
          password (-> (into-array Byte/TYPE (concat salt (.getBytes password)))
                       buddy.core.hash/sha256
                       buddy.core.codecs/bytes->hex)]
      (model/transact [{:db/id #db/id[db.part/user -1]
                        :user/name name
                        :user/password password
                        :user/salt salt
                        :user/role (keyword "user.role" role)}])
      (redirect "/admin/users/"))))

