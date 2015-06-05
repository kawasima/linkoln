(ns linkoln.core
  (:use [compojure.core]
        [ring.middleware.defaults :only [wrap-defaults site-defaults api-defaults]]
        [ring.middleware.anti-forgery :only [*anti-forgery-token*]]
        [ring.util.anti-forgery :only [anti-forgery-field]]
        [ring.util.response :only [redirect response content-type]]
        [environ.core :only [env]]
        [buddy.auth :only [authenticated?]]
        [buddy.auth.backends.session :only [session-backend]]
        [buddy.auth.middleware :only [wrap-authentication wrap-authorization]]
        [buddy.auth.accessrules :only [wrap-access-rules]]
        [hiccup.core :only [html]]
        [hiccup.page :only [html5 include-css include-js]])
  (:require [clojure.java.io :as io]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [gring.core :as gring]
            (linkoln [model :as model]
                     [exercise :as exercise]
                     [style :as style]))
  (:import [org.eclipse.jgit.transport.resolver FileResolver]
           [java.net URI]))

(defn init []
  (model/create-schema)
  (when-not (model/query '{:find [?s .]
                           :in [$]
                           :where [[?s :user/name "admin"]]})
    (model/transact [{:db/id #db/id[db.part/user -1]
                      :user/name "admin"
                      :user/password "admin"
                      :user/role :user.role/teacher}])))

(defn- layout [& contents]
  (html5
   [:head
    [:meta {:name "viewport" :content "width=device=width, initial-scale=1"}]
    (include-css "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/1.12.2/semantic.min.css"
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
  (layout
   [:h2.ui.header
    [:i.user.icon]
    [:div.content "Your course"]]
   [:table.ui.celled.table
    [:thead
     [:tr
      [:th {:colspan 3} "Exercises"]]]
    [:tbody
     (for [exercise exercises]
       (let [status (get-in exercise [:answer/status :db/ident])]
         [:tr
          [:td (:exercise/name exercise)]
          [:td (case status
                 :answer.status/started
                 [:div
                  (str (name (:scheme req)) "://" (:server-name req) ":" (:server-port req) "/git/" (name username) "/" (:exercise/name exercise) ".git")]
                 :answer.status/submitted
                 [:div
                  "submitted"]
                 [:pre (exercise/get-readme exercise)])]
          [:td (case status
                 :answer.status/started
                 [:a.ui.negative.button {:href ""} "Abandon"]
                 :answer.status/submitted
                 ""
                 [:form.ui.form {:action (str "/exercise/" (:db/id exercise) "/fork")  :method "POST"}
                  (anti-forgery-field)
                  [:button.ui.positive.button {:type "submit"} "solve"]])]]))]]
   (when (= role :user.role/teacher)
     [:a.ui.primary.button {:href "/admin/exercises/new"} "New exercise"])))

(defn- new-exercise [req]
  (layout
   [:form.ui.form {:action "/admin/exercises/new" :method "POST"}
    (anti-forgery-field)
    [:div.field
     [:label "Name"]
     [:input {:type "text" :name "name" :placeholder "Excercise name"}]]
    [:div.field
     [:label "URL"]
     [:input {:type "text" :name "url" :placeholder ""}]]
    [:button.ui.positive.button {:type "submit"} "Save"]]))

(defn- list-users [users]
  (layout
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
  (layout
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

(defn login-get [req]
  (layout
   [:div.login-screen
    [:form.ui.form {:method "post"}
     (anti-forgery-field)
     [:fieldset
      [:div.ui.right.icon.input
       [:input {:type "text" :name "username" :placeholder "username"}]
       [:i.user.icon]]
      [:div.ui.right.icon.input
       [:input {:type "password" :name "password" :placeholder "password"}]
       [:i.lock.icon]]
      [:button.fluid.ui.positive.button {:type "submit"} "Login"]]]]))

(defn login-post [req]
  (let [username (get-in req [:form-params "username"])
        password (get-in req [:form-params "password"])]
    (if-let [user (model/query '{:find [(pull ?s [:* {:user/role [:db/ident]}]) .]
                                    :in [$ ?uname ?passwd]
                                    :where [[?s :user/name ?uname]
                                            [?s :user/password ?passwd]]} username password)] 
      (-> (redirect (get-in req [:query-params "next"] "/"))
          (assoc-in [:session :identity] (keyword username))
          (assoc-in [:session :role] (get-in user [:user/role :db/ident])))
      (login-get req))))

(defn logout []
  (-> (redirect "/")
      (assoc :session {})))

(defroutes app-routes
  (GET "/" req
    (redirect "/exercises/"))
  (GET "/login" req (login-get req))
  (POST "/login" req (login-post req))
  (GET "/logout" [] (logout))
  (GET "/exercises/" req
    (let [exercises (model/query '{:find [[(pull ?exercise [:*]) ...]]
                                   :where [[?exercise :exercise/name]]})
          username (name (get-in req [:session :identity]))]
      (list-exercise (map #(if-let [answer (model/query '{:find [(pull ?a [:* {:answer/status [:db/ident]}]) .]
                                                  :in [$ ?e ?username]
                                                  :where [[?e :exercise/answers ?a]
                                                          [?a :answer/user   ?s]
                                                          [?s :user/name ?username]]} (:db/id %) username)]
                     (merge % answer)
                     %) exercises)
                     req)))

  (POST "/exercise/:exercise-id/fork" {{exercise-id :exercise-id} :params :as req}
    (let [username (name (get-in req [:session :identity]))
          user (model/query '{:find [?s .] :in [$ ?name] :where [[?s :user/name ?name]]} username)]
      (exercise/start-to-solve exercise-id username)
      (model/transact [{:db/id #db/id[db.part/user -1]
                        :answer/user user
                        :answer/status :answer.status/started}
                       [:db/add (Long/parseLong exercise-id) :exercise/answers #db/id[db.part/user -1]]])
      (redirect "/exercises/")))
  
  (GET "/admin/exercises/" req
    (let [exercises (model/query '{:find [[(pull ?exercise [:*]) ...]]
                                   :where [[?exercise :exercise/name]]})
          username (name (get-in req [:session :identity]))]
      (list-exercise (map #(if-let [answer (model/query '{:find [(pull ?a [:* {:answer/status [:db/ident]}]) .]
                                                  :in [$ ?e ?username]
                                                  :where [[?e :exercise/answers ?a]
                                                          [?a :answer/user   ?s]
                                                          [?s :user/name ?username]]} (:db/id %) username)]
                     (merge % answer)
                     %) exercises)
                     req)))
  (GET "/admin/exercises/new" req (new-exercise req))
  (POST "/admin/exercises/new" {{:keys [name url]} :params :as req}
    (exercise/fork-exercise name url)
    (model/transact [{:db/id #db/id[db.part/user -1]
                      :exercise/name name
                      :exercise/url  (URI/create url)}])
    (redirect "/admin/exercises/"))
  
  (GET "/admin/users/" req
    (let [users (model/query '{:find [[(pull ?u [:* {:user/role [:db/ident]}]) ...]]
                               :where [[?u :user/name]]})]
      (list-users users)))
  (GET "/admin/users/new" req (new-user req))
  (POST "/admin/users/new" {{:keys [name password role]} :params :as req}
    (model/transact [{:db/id #db/id[db.part/user -1]
                      :user/name name
                      :user/password password
                      :user/role (keyword "user.role" role)}])
    (redirect "/admin/users/"))
  (GET "/css/linkoln.css" []
    (-> {:body (style/build)}
        (content-type "text/css")))
  (route/not-found "Not Found"))

(defn unauthorized-handler
  [req meta]
  (if (authenticated? req)
    (redirect "/exercises/")
    (redirect (format "/login?next=%s" (:uri req)))))

(swap! gring/config assoc :repository-resolve-fn
       (fn [req]
         (let [{{owner :owner repo-name :name} :params} req]
           (.open (FileResolver. (io/file "git" owner) true) nil repo-name))))

(defn admin? [request]
  (and (authenticated? request)
       (= (get-in request [:session :role]) :user.role/teacher)))
(def app
  (let [rules [{:pattern #"^/exercises/.*" :handler authenticated?}
               {:pattern #"^/admin/.*" :handler admin?}]
        backend (session-backend {:unauthorized-handler unauthorized-handler})]
    (routes
     (context ["/git/:owner" :owner #"[0-9A-Za-z\-\.]+"] [owner]
       (wrap-defaults gring/git-routes api-defaults))
     (-> app-routes
        (wrap-access-rules {:rules rules :policy :allow})
        (wrap-authentication backend)
        (wrap-authorization backend)
        (wrap-defaults site-defaults)))))

