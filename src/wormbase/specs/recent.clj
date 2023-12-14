(ns wormbase.specs.recent
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [java-time.api :as jt]
   [spec-tools.spec :as sts]
   [spec-tools.core :as stc]
   [wormbase.util :as wu]
   [wormbase.specs.agent :as wsa]
   [wormbase.specs.provenance :as wsp]))

(def ^{:dynamic true} *default-days-ago* 60)

(def agents (-> wsa/all-agents :spec (disj :agent/importer)))

(def agent-names (->> agents (map name) set))

(s/def ::from (stc/spec
               {:spec (s/nilable inst?)
                :swagger/example (wu/format-java-date (wu/days-ago *default-days-ago*))
                :description (str "Defaults to the date-time at "
                                  *default-days-ago*
                                  " days ago.")}))

(s/def ::until (stc/spec {:spec (s/nilable inst?)
                          :swagger/example (jt/instant)
                          :description "Defaults to the current date-time."}))

(s/def ::activities (stc/spec {:spec (s/coll-of ::wsp/temporal-change)}))

(s/def ::how (stc/spec {:spec (s/and sts/keyword? agents)}))


(s/def ::agent (stc/spec {:spec (s/and sts/string? agent-names)
                          :swagger/example "web"
                          :description (str "Set to filter events by the type of agent. "
                                            "(One of "
                                            (str/join ", " (sort agent-names))
                                            ") .Defaults to all agents.")}))
