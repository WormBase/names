(ns org.wormbase.test-utils
  (:require [compojure.api.routes :as routes]
            [compojure.api.middleware :as mw]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :as t]
            [datomic.api :as d]
            [muuntaja.core :as m]
            [peridot.core :as p]
            [spec-tools.core :as stc])
  (:import (java.io InputStream)))

(def muuntaja (m/create))

(defn read-body [body]
  (if (instance? InputStream body)
    (slurp body)
    body))

(defn parse-body [body]
  (let [body (read-body body)
        body (if (instance? String body)
               (m/decode muuntaja "application/json" body)
               body)]
    body))

(defn extract-schema-name [ref-str]
  (last (str/split ref-str #"/")))

(defn find-definition [spec ref]
  (let [schema-name (keyword (extract-schema-name ref))]
    (get-in spec [:definitions schema-name])))

(defn json-stream [x]
  (m/encode muuntaja "application/json" x))

(def json-string (comp slurp json-stream))

(defn parse [x]
  (m/decode muuntaja "application/json" x))

(defn follow-redirect [state]
  (if (some-> state :response :headers (get "Location"))
    (p/follow-redirect state)
    state))

(def ^:dynamic *async?*
  (= "true" (System/getProperty "compojure-api.test.async")))

(defn- call-async [handler request]
  (let [result (promise)]
    (handler request #(result [:ok %]) #(result [:fail %]))
    (if-let [[status value] (deref result 1500 nil)]
      (if (= status :ok)
        value
        (throw value))
      (throw
       (Exception. (str "Timeout while waiting for the request handler. "
                        request))))))

(defn call
  "Call handler synchronously or asynchronously depending on *async?*."
  [handler request]
  (if *async?*
    (call-async handler request)
    (handler request)))

(defn raw-get* [app uri & [params headers]]
  (let [{{:keys [status body headers]} :response}
        (-> (cond->> app *async?* (partial call-async))
            (p/session)
            (p/request uri
                       :request-method :get
                       :params (or params {})
                       :headers (or headers {}))
            follow-redirect)]
    [status (read-body body) headers]))

(defn get* [app uri & [params headers]]
  (let [[status body headers]
        (raw-get* app uri params headers)]
    [status (parse-body body) headers]))

(defn form-post* [app uri params]
  (let [{{:keys [status body]} :response}
        (-> (p/session app)
            (p/request uri
                       :request-method :post
                       :params params))]
    [status (parse-body body)]))

(defn raw-put-or-post* [app uri method data content-type headers]
  (let [{{:keys [status body]} :response}
        (-> (p/session app)
            (p/request uri
                       :request-method method
                       :headers (or headers {})
                       :content-type (or content-type "application/json")
                       :body (.getBytes data)))]
    [status (read-body body)]))

(defn raw-post* [app uri & [data content-type headers]]
  (raw-put-or-post* app uri :post data content-type headers))

(defn post* [app uri & [data]]
  (let [[status body] (raw-post* app uri data)]
    [status (parse-body body)]))

(defn put* [app uri & [data]]
  (let [[status body] (raw-put-or-post* app uri :put data nil nil)]
    [status (parse-body body)]))

(defn headers-post* [app uri headers]
  (let [[status body] (raw-post* app uri "" nil headers)]
    [status (parse-body body)]))

;;
;; ring-request
;;

(defn ring-request [m format data]
  {:uri "/echo"
   :request-method :post
   :body (m/encode m format data)
   :headers {"content-type" format
             "accept" format}})

;;
;; get-spec
;;

(defn extract-paths [app]
  (-> app routes/get-routes routes/all-paths))

(defn get-spec [app]
  (let [[status spec] (get* app "/swagger.json" {})]
    (assert (= status 200))
    (if (:paths spec)
      (update-in spec [:paths] (fn [paths]
                                 (into
                                   (empty paths)
                                   (for [[k v] paths]
                                     [(if (= k (keyword "/"))
                                        "/" (str "/" (name k))) v]))))
      spec)))

(defn status-is? [status expected-status body]
  (t/is (= status expected-status)
        (format "Response body did not contain expected data:\n%s"
                (if-let [suspec (:spec body)]
                  (pr-str (stc/deserialize suspec))
                  (pr-str body)))))

(defn- map->set [m]
  (assert (map? m))
  (some->> (apply vector m)
           (flatten)
           (partition 2)
           (set)))

(defn body-contains? [body expected-data spec]
  (assert (map? expected-data) "expected-data must be a map")
  (let [exp-data (map->set expected-data)
        act-data (map->set body)]
    (t/is (set/subset? exp-data act-data)
          (str/join "\n" ["Expected data:"
                          (pr-str exp-data)
                          "not found in response body:"
                          (pr-str body)]))
    (when-let [rspec (:spec body)]
      (clojure.pprint/pprint (stc/deserialize rspec)))))
