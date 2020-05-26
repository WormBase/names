(ns wormbase.names.coercion
  (:require
   [compojure.api.coercion.core :as cc]
   [compojure.api.coercion.spec :as spec-coercion]
   [spec-tools.core :as st]))

;;; Modified copies of the compojure-api's coercion to prevent stripping of keys.
;;; TODO!!: This neesd to be kept in sync with compojure-api releases if the implementation changes.
;;;         Add developer documentation.

(def string-transformer
  (st/type-transformer st/string-transformer {:name :string}))

(def json-transformer
  (st/type-transformer st/json-transformer {:name :json}))

(defn non-stripping-spec-keys-coercion
  "Creates a new spec coercion without the extra-keys stripping in response formats."
  []
  (let [options (-> spec-coercion/default-options
                    (assoc-in
                     [:body :formats]
                     {"application/json" json-transformer
                      "application/msgpack" json-transformer
                      "application/x-yaml" json-transformer})
                    (assoc-in [:body :string :default] string-transformer))]
    (spec-coercion/create-coercion options)))

(def ^{:doc "Custom coercion that doesn't strip keys from specs during response processing."}
  pure-spec (non-stripping-spec-keys-coercion))

(defmethod cc/named-coercion :pure-spec [_] pure-spec)


