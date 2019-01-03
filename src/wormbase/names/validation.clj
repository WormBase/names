(ns wormbase.names.validation
  (:require
   [clojure.spec.alpha :as s]
   [datomic.api :as d]))

(defmulti validate-names (fn [request data]
                           (some-> data keys first namespace keyword)))

(defmethod validate-names :gene [request data]
   (let [db (:db request)
         species-lur (s/conform :gene/species (:gene/species data))
         species-ent (d/pull db '[*] species-lur)]
    (if (empty? data)
      (throw (ex-info "No names to validate (empty data)"
                      {:type :user/validation-error})))
    (if-not (:db/id species-ent)
      (throw (ex-info "Invalid species specified"
                      {:invalid-species species-lur
                       :type :user/validation-error})))
    (let [patterns ((juxt :species/cgc-name-pattern :species/sequence-name-pattern) species-ent)
          regexps (map re-pattern patterns)
          name-idents [:gene/cgc-name :gene/sequence-name]]
      (doseq [[regexp name-ident] (partition 2 (interleave regexps name-idents))]
        (when-let [gname (name-ident data)]
          (when-not (re-matches regexp gname)
            (when-not (and (empty? (get data gname))
                           (= gname :gene/cgc-name))
              (throw
               (ex-info "Invalid name"
                        {:type :user/validation-error
                         :data {:problems
                                {:invalid
                                 {:name gname :ident name-ident}}}}))))))
      data)))


