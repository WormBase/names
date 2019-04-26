(ns wormbase.names.response-formats
  (:require
   [muuntaja.core :as muuntaja]))

(def json (muuntaja/create))

(defn process-content [processor m content]
  (let [m* (or m json)
        fmt (muuntaja/default-format m*)]
    (processor m* fmt content)))

(defn decode-content [content & [m]]
  (process-content muuntaja/decode m content))

(defn encode-content [content & [m]]
  (slurp (process-content muuntaja/encode m content)))




