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
  (model/create-schema))

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
       "Linkoln"]]]
    [:div.wrapper contents]]))

(defn- list-exercise [exercises]
  (layout
   [:h2.ui.header
    [:i.student.icon]
    [:div.content "Your course"]]
   [:table.ui.celled.table
    [:thead
     [:tr
      [:th {:colspan 2} "Exercises"]]]
    [:tbody
     (for [exercise exercises]
       [:tr
        [:td (:exercise/name exercise)]
        [:td (if-let [status (:answer/status exercise)]
               [:div
                [:div.ui.label (name (:db/ident status))]
                (:exercise/url exercise)]
               [:form.ui.form {:action (str "/exercise/" (:db/id exercise) "/fork")  :method "POST"}
                (anti-forgery-field)
                [:button.ui.positive.button {:type "submit"} "solve"]])]])]]
   [:a.ui.primary.button {:href "/exercise/new"} "New exercise"]))

(defn- new-exercise [req]
  (layout
  req
   [:form.ui.form {:action "/exercise/new" :method "POST"}
    (anti-forgery-field)
    [:div.fields
     [:div.field
      [:label "Name"]
      [:input {:type "text" :name "name" :placeholder "Excercise name"}]]
     [:div.field
      [:label "URL"]
      [:input {:type "text" :name "url" :placeholder ""}]]]
    [:button.ui.button {:type "submit"} "Save"]]))

(defn- admin [req]
  (layout
   [:h1 "Logged in as " [:strong (name (get-in req [:session :identity]))]]
   [:a {:href "/"} "To Home"]
   (if (authenticated? req)
     [:a {:href "/logout"} "Logout"])))

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
  (let [username (get-in req [:form-params "username"])]
    (when-not (model/query '{:find [?s .]
                             :in [$ ?uname]
                             :where [[?s :student/name ?uname]]} username)
      (model/transact [{:db/id #db/id[db.part/user -1]
                        :student/name username}]))
    (-> (redirect (get-in req [:query-params "next"] "/"))
        (assoc-in [:session :identity] (keyword username)))))

(defn logout []
  (-> (redirect "/")
      (assoc :session {})))

(defroutes app-routes
  (GET "/" req
    (redirect "/exercise/"))
  (GET "/exercise/" req
    (let [exercises (model/query '{:find [[(pull ?exercise [:*]) ...]]
                                   :where [[?exercise :exercise/name]]})
          username (name (get-in req [:session :identity]))]
      (list-exercise (map #(if-let [answer (model/query '{:find [(pull ?a [:* {:answer/status [:db/ident]}]) .]
                                                  :in [$ ?e ?username]
                                                  :where [[?e :exercise/answers ?a]
                                                          [?a :answer/student   ?s]
                                                          [?s :student/name ?username]]} (:db/id %) username)]
                     (merge % answer)
                     %) exercises))))
  (GET "/admin/" req (admin req))
  (GET "/login" req (login-get req))
  (POST "/login" req (login-post req))
  (GET "/logout" [] (logout))
  (GET "/exercise/new" req (new-exercise req))
  (POST "/exercise/new" {{:keys [name url]} :params :as req}
    (model/transact [{:db/id #db/id[db.part/user -1]
                      :exercise/name name
                      :exercise/url  (URI/create url)}])
    (redirect "/exercise/"))
  (POST "/exercise/:exercise-id/fork" {{exercise-id :exercise-id} :params :as req}
    (let [username (name (get-in req [:session :identity]))
          student (model/query '{:find [?s .] :in [$ ?name] :where [[?s :student/name ?name]]} username)]
      (exercise/start-to-solve exercise-id username)
      (model/transact [{:db/id #db/id[db.part/user -1]
                        :answer/student student
                        :answer/status :answer.status/started}
                       [:db/add (Long/parseLong exercise-id) :exercise/answers #db/id[db.part/user -1]]])))
  (GET "/css/linkoln.css" []
    (-> {:body (style/build)}
        (content-type "text/css")))
  (route/not-found "Not Found"))

(defn unauthorized-handler
  [req meta]
  (if (authenticated? req)
    (redirect "/exercise")
    (redirect (format "/login?next=%s" (:uri req)))))

(swap! gring/config assoc :repository-resolve-fn
       (fn [req]
         (let [{{owner :owner repo-name :name} :params} req]
           (.open (FileResolver. (io/file "git" owner) true) nil repo-name))))

(def app
  (let [rules [{:pattern #"^/(admin|exercise)/.*" :handler authenticated?}]
        backend (session-backend {:unauthorized-handler unauthorized-handler})]
    (routes
     (context ["/git/:owner" :owner #"[0-9A-Za-z\-\.]+"] [owner]
       (wrap-defaults gring/git-routes api-defaults))
     (-> app-routes
        (wrap-access-rules {:rules rules :policy :allow})
        (wrap-authentication backend)
        (wrap-authorization backend)
        (wrap-defaults site-defaults)))))

