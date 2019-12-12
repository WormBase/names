(ns wormbase.names.coercion
  (:require
   [reitit.coercion.spec :as rcs]
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
  (let [options (-> rcs/default-options
                    (assoc-in [:transformers :body :formats] {"application/json" json-transformer})
                    (assoc-in [:transformers :body :string :default] string-transformer))]
    (rcs/create options)))

(def ^{:doc "Custom coercion that doesn't strip keys from specs during response processing."}
  open-spec (non-stripping-spec-keys-coercion))


