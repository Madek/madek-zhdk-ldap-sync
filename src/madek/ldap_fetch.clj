(ns madek.ldap-fetch
  (:refer-clojure :exclude [str keyword])

  (:require
    [madek.utils :refer [str keyword]]

    [clj-ldap.client :as ldap]
    [cheshire.core :as cheshire]

    [clojure.tools.logging :as logging]
    ))


;;; fetch-groups ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def last-fetch-result* (atom {}))

(defn fetch-groups [options]
  (let [conn-params {:host (:ldap-host options)
                     :bind-dn (:ldap-bind-dn options)
                     :ssl? true
                     :password (:ldap-password options)}]
    (with-open
      [conn (ldap/connect conn-params)]
      (->> (ldap/search-all
             conn
             "OU=_Distributionlists,OU=_ZHdK,DC=ad,DC=zhdk,DC=ch"
             { :attributes [:cn
                            :name
                            :extensionAttribute1
                            :extensionAttribute3
                            :displayName]})
           (reset! last-fetch-result*)))))


;;; map and filter ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def last-mapped-data* (atom {}))

(defn map-groups
  "Converts a seq of groups each containing ldad attributes into a map from
  institutional_id to row data as used in Madek. Pipes the data through
  cheshire to json and back for consisten keyword encoding."
  [ldap-groups]
  (->>
    (->
      (->> ldap-groups
           (map (fn [row]
                  (logging/debug {:row row})
                  (->> row
                       (map (fn [[k v]]
                              (let [new-key (case k
                                              :name :institutional_name
                                              :extensionAttribute3 :institutional_id
                                              :extensionAttribute1 :name
                                              k)]
                                [new-key v])))
                       (sort)
                       (into (empty row))
                       (#(select-keys % [:institutional_id
                                         :institutional_name
                                         :name ])))))
           (filter :institutional_id)
           (filter :name)
           (map (fn [g] [(:institutional_id g) g]))
           (into {}))
      cheshire/generate-string
      (cheshire/parse-string keyword))
    (reset! last-mapped-data*)))


;;; run ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn run [options]
  (logging/info "Fetching LDAP data .... ")
  (let [ldap-groups (fetch-groups options)]
    (logging/info "fetch LDAP data done.")
    {:institutional-groups (map-groups ldap-groups)}))
