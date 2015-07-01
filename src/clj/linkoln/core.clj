(ns linkoln.core
  (:use [compojure.core]
        [ring.middleware.defaults :only [wrap-defaults site-defaults api-defaults]]
        [ring.middleware.anti-forgery :only [*anti-forgery-token*]]
        [ring.util.anti-forgery :only [anti-forgery-field]]
        [ring.util.response :only [redirect response content-type]]
        [environ.core :only [env]]
        [buddy.core.hash :only [sha256]]
        [buddy.auth :only [authenticated?]]
        [buddy.auth.backends.session :only [session-backend]]
        [buddy.auth.backends.httpbasic :only [http-basic-backend]]
        [buddy.auth.middleware :only [wrap-authentication wrap-authorization]]
        [buddy.auth.accessrules :only [wrap-access-rules]]
        [hiccup.core :only [html]]
        [hiccup.page :only [html5 include-css include-js]]
        [linkoln.admin :only [admin-routes]])
  (:require [clojure.java.io :as io]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [buddy.core.nonce :as nonce]
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
    (let [salt (nonce/random-nonce 16)]
      (model/transact [{:db/id #db/id[db.part/user -1]
                        :user/name "admin"
                        :user/salt salt
                        :user/password (-> (into-array Byte/TYPE (concat salt (.getBytes "admin")))
                                           sha256
                                           buddy.core.codecs/bytes->hex)
                        :user/role :user.role/teacher}]))))

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
                 (:exercise/description exercise))]
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

(defn auth-by-password [username password]
  (model/query '{:find [(pull ?s [:* {:user/role [:db/ident]}]) .]
                 :in [$ ?uname ?passwd]
                 :where [[?s :user/name ?uname]
                         [?s :user/salt ?salt]
                         [(concat ?salt ?passwd) ?passwd-seq]
                         [(into-array Byte/TYPE ?passwd-seq) ?passwd-bytes]
                         [(buddy.core.hash/sha256 ?passwd-bytes) ?hash]
                         [(buddy.core.codecs/bytes->hex ?hash) ?hash-hex]
                         [?s :user/password ?hash-hex]]} username password))
(defn login-post [req]
  (let [username (get-in req [:form-params "username"])
        password (get-in req [:form-params "password"])]
    (if-let [user (auth-by-password username password)]
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
  (println (get-in request [:session]))
  (and (authenticated? request)
       (= (get-in request [:session :role]) :user.role/teacher)))
(def app
  (let [rules [{:pattern #"^/exercises/.*" :handler authenticated?}]
        backend (session-backend {:unauthorized-handler unauthorized-handler})
        backend-git (http-basic-backend {:realm "Linkoln" :authfn (fn [req auth]
                                                                    (if (get-in req [:params :owner])
                                                                      (auth-by-password (:username auth) (:password auth))))})]
    (routes
     (context ["/git/:owner" :owner #"[0-9A-Za-z\-\.]+"] [owner]
       (-> gring/git-routes
           (wrap-access-rules {:rules [{:pattern #".*"
                                        :handler (fn [req]
                                                   (authenticated? req))}]
                               :policy :reject})
           (wrap-authentication backend-git)
           (wrap-authorization backend-git)
           (wrap-defaults api-defaults)))
     (-> (routes
          (context "/admin" []
            (-> admin-routes
                (wrap-access-rules {:rules [{:pattern #".*" :handler admin?}]
                                    :policy :allow})))
          (-> app-routes
              (wrap-access-rules {:rules rules :policy :allow})))
         (wrap-authentication backend)
         (wrap-authorization backend)
         (wrap-defaults site-defaults)))))

