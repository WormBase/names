(ns wormbase.names.validation)

(defmulti validate-names (fn [_ data]
                           (some->> data
                                    (keys)
                                    (filter qualified-keyword?)
                                    (first)
                                    (namespace)
                                    (keyword))))
