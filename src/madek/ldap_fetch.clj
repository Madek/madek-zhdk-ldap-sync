(ns madek.ldap-fetch
  (:require
    [clj-ldap.client :as ldap]

    [clojure.tools.logging :as logging]
    ))


;;; fetch ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def last-fetch-result* (atom {}))

(defn fetch [options]
  (let [conn-params {:host (:ldap-host options)
                     :bind-dn (:ldap-bind-dn options)
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

(defn map-data [data]
  (->> data
       (map (fn [row]
              (logging/debug {:row row})
              (->> row
                   (map (fn [[k v]]
                          (let [new-key (case k
                                          :name :institutional_group_name
                                          :extensionAttribute3 :institutional_group_id
                                          :extensionAttribute1 :name
                                          :displayName :display_name
                                          k)]
                            [new-key v])))
                   (sort)
                   (into (empty row))
                   (#(select-keys % [:name :institutional_group_id :institutional_group_name]))
                   )))
       (filter :institutional_group_id)
       (filter :name)
       (sort-by  :institutional_group_name)
       (reset! last-mapped-data*)))


;;; run ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn run [options]
  (logging/info "Fetching LDAP data .... ")
  (let [data (fetch options)]
    (logging/info "Fetching LDAP done.")
    (map-data data)))
