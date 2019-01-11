(ns wormbase.aws-eb-setup
  "Used for lein release tasks."
  (:require [clojure.string :as str])
  (:gen-class))

(def proj-pattern #"wormbase/names:[\d.]+")

(defn replace-version [content new-version]
  (str/replace content proj-pattern (str "wormbase/names:" new-version)))

(defn update-eb-json! [eb-json-path]
  (let [version (System/getProperty "wormbase-names.version")
        curr-content (slurp eb-json-path)
        new-content (slurp eb-json-path)]
    (split eb-json-path (-> eb-json-path
                            slurp
                            (replace-version version)))))
(defn- main
  [& args]
  (update-eb-json! "./Dockerrun.aws.json"))
