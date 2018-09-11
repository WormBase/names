(ns wormbase.names.coercion
  [compojure.api.coercion.spec :as spec-coercion]
  [spec-tools.core :as stc]
  [spec-tools.transform :as stt])

;;; Modified copies of the original transformers:
;;; https://github.com/metosin/compojure-api/blob/master/src/compojure/api/coercion/spec.clj#L16-L35
;;
;; The only change to remove the `spec-tools.transform/strip-extra-keys-type-decoders` from
;; the decoders applied to response formats.

(def string-transformer
  (stc/type-transformer
    {:name :string
     :decoders stt/string-type-decoders
     :encoders stt/string-type-encoders
     :default-encoder stt/any->any}))

(def json-transformer
  (stc/type-transformer
    {:name :json
     :decoders stt/json-type-decoders
     :encoders stt/json-type-encoders
     :default-encoder stt/any->any}))

(defn non-stripping-spec-keys-coercion
  "Creates a new spec coercion without the extra-keys stripping in response formats."
  []
  (let [mimetypes (-> spec-coercion/default-options :body keys)
        options (-> spec-coercion/default-options
                    (assoc-in
                     [:body :formats]
                     (zipmap mimetypes (repeat json-transformer)))
                    (assoc-in [:body :string :default] string-transformer))]
    (spec-coercion/create-coercion options)))

(def ^:{:doc "Custom coercion that doesn't strip keys from specs during response processing."}
  spec (non-stripping-spec-keys-coercion))


