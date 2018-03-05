(ns org.wormbase.names.auth
  (:require
   [clojure.set :refer [rename-keys]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [buddy.auth :as auth]
   [buddy.auth.backends :as auth-backends]
   [buddy.auth.middleware :as auth-mw]
   [buddy.auth.protocols :as auth-protocols]
   [buddy.core.codecs :as codecs]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [org.wormbase.names.util :as util]
   [org.wormbase.specs.auth :as auth-spec]
   [org.wormbase.specs.user :as user-spec]
   [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
   [ring.middleware.session.cookie :as ring-sc]
   [ring.util.codec :as ruc]
   [ring.util.http-response :as http-response]))

;; TODO: dispatch to a different (buddy)backend credentials store
;;       depending on the request's user-agent
;;       If requset is via a web browser,
;;       then we want to get credentials from the OS's environ/beanstalk env vars
;;
;;       If request is via a Perl/Python script,
;;       then we want to retrieve the credentials from the configured file on disk
;;       (or else make people set env vars every time they run their scripts?)

;;; ENSURE NOT TO LEAK INFO (e.g CLIENT_SECRET, via any logging)

(def ^:private refresh-token-api "http://www.googleapis.com/oauth2/v4/token")

(def ^:private revoke-token-api "https://accounts.google.com/o/oauth2/revoke")

(def ^:private people-api "https://people.googleapis.com/v1/people/me")

(def ^:private people-api-params ["names" "emailAddresses"])

(defn refresh-token [request]
  ;; CLIENT_ID and CLIENT_SECRET need to match those supplied by
  ;; the client.
  ;; Client configures themselves via Google API console, and passes
  ;; access_token/refresh_token
  ;;;
  ;;; --------------- THIS APP --------------------
  ;; Needs to be configured with CLIENT_ID and CLIENT_SECRET from the
  ;; project environment (lein vars or environ)
  (let [credentials (:credentials request)]
    (http/post
     refresh-token-api
     {:form-params (merge (select-keys credentials [:client_id
                                                    :client_secret
                                                    :refresh_token])
                          {:grant_type "refresh_token"})})))

;; TODO
(defn- revoke-token [request]
  (assert false "TBD"))

(defn- google-people-identify
  "Identify via the google people API.
  Returns a mapping of username and token.
  Names in the resultant map replace google's keys normalised to lower-case
  as used by the rest of the application."
  [data]
  (let [their-key-names ["displayName" "value"]
        our-key-names [:name :email]
        key-path-selector ["metadata" "primary"]
        selected (->> (vals data)
                      (apply concat)
                      (filter #(get-in % key-path-selector))
                      (map #(select-keys % their-key-names))
                      (into {}))
        kmap (apply array-map (interleave their-key-names our-key-names))]
    (rename-keys selected kmap)))

;; Factored into a separate function so it can mocked out in tests
;; with `alter-var-root`
(s/fdef who-am-i
        :args (s/and string? #(str/starts-with? % people-api))
        :ret (s/and map? not-empty))
(defn who-am-i 
  "Resolve a user via a URL. Returns a map."
  [url]
  (try
    (http/get url)
    (catch Exception e
      nil)))

(s/fdef identify
        :args (s/keys :req-un [::auth-spec/headers])
        :ret ::user-spec/identified)
(defn identify
  "Resolve the user's email details from the `request`.
  Returns `nil` if the user cannot be found."
  [request]
  (let [backend (auth-backends/token {:token-name "Bearer"
                                      :authfn identity})
        params {:personFields "names,emailAddresses"
                :access_token (auth-protocols/-parse backend request)}
        url (str people-api "?" (ruc/form-encode params))
        ;; TODO: return nil or should this try to use refresh_token
        ;; upon 401?
        response (who-am-i url)]
    (some-> response
            :body
            json/parse-string
            google-people-identify)))

(defn- cookie-secret-key [app-config]
  (-> app-config :cookie-secret codecs/hex->bytes))

(defn- oauth2-token-flow [request authdata]
  (if-let [user-info (identify request)]
    user-info
    (http-response/unauthorized!)))

(def session-backend (auth-backends/session))

(def token-backend (auth-backends/token {:authfn oauth2-token-flow
                                         :token-name "Bearer"}))

(defn wrap-app-session
  [handler]
  (let [conf (util/read-app-config)
        cookie-store (ring-sc/cookie-store {:key (cookie-secret-key conf)})
        cookie-attrs {:http-only true :same-site :lax}
        settings (-> api-defaults
                     (assoc-in [:session] {:cookie-attrs cookie-attrs
                                           :store cookie-store})
                     (assoc :cookies cookie-attrs))]
    (-> handler
        ;; look in the session to play nice with Google (e.g avoid rate-limiting)
        (auth-mw/wrap-authentication session-backend token-backend)
        (auth-mw/wrap-authorization session-backend)
        (wrap-defaults settings))))

(defn authenticated? [req]
  (println "------------------------ CHECKING AUTH: ---------------------")
  (println)
  (auth/authenticated? req))

;; Requires that (:identity req) is a map containing the role.
(defn admin [req]
  (and (authenticated? req)
       (#{:admin} (:role (:identity req)))))
