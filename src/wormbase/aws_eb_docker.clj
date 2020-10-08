(ns wormbase.aws-eb-docker
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [wormbase.util :as wu]))

(def proj-pattern #"wormbase/names:[^\"]+")

(defn replace-version [content new-version]
  (str/replace content proj-pattern (str "wormbase/names:" new-version)))

(defn update-eb-json! [eb-json-path new-version]
  (spit eb-json-path (-> eb-json-path
                         slurp
                         (replace-version new-version))))
(defn -main
  [& _]
  (let [proj-meta (wu/read-app-config "meta.edn")
        version (:version proj-meta)
        target-filename "Dockerrun.aws.json"
        files (map io/file ["Dockerrun.aws.json.template" target-filename])]
    (when-not (.exists (last files))
      (apply io/copy files))
    (update-eb-json! target-filename version)))
