(ns wormbase.aws-eb-docker
  (:require
   [clojure.string :as str]
   [wormbase.names.metav :as wn-metav]))

(def proj-pattern #"wormbase/names:[\d.]+")

(defn replace-version [content new-version]
  (str/replace content proj-pattern (str "wormbase/names:" new-version)))

(defn update-eb-json! [eb-json-path]
  (let [curr-content (slurp eb-json-path)
        new-content (slurp eb-json-path)]
    (spit eb-json-path (-> eb-json-path
                           slurp
                           (replace-version wn-metav/version)))))
(defn -main
  [& args]
  (println wn-metav/version)
  (update-eb-json! "./Dockerrun.aws.json"))
