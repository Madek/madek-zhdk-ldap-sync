(ns madek.sync
  (:refer-clojure :exclude [str keyword])

  (:require
    [madek.utils :refer [str keyword]]

    [cheshire.core :as cheshire]
    [clojure.pprint :refer [pprint]]
    [clojure.set :as set]
    [json-roa.client.core :as roa]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))

(defn api-root [{base-url :madek-base-url token :madek-token}]
  (roa/get-root (str base-url "/api")
                :default-conn-opts {:basic-auth token}))

(defn check-connection!
  "Retrieves the auth-info relation and causes an error to be thrown
   if there is an authentication problem."
  [root]
  (let [auth-info (-> root
                      (roa/relation :auth-info)
                      (roa/get {}))]
    (logging/debug 'auth-info auth-info)))


;### create new groups ########################################################

(defn create-group [data options root]
  (let [id (-> data :institutional_group_id str)
        data (assoc data :type "InstitutionalGroup") ]
    (logging/info "Creating group " id " with data " (cheshire/generate-string data))
    (-> root
        (roa/relation :groups)
        (roa/request {} :post
                     {:headers {"Content-Type" "application/json"}
                      :body (cheshire/generate-string data)})
        )))

(defn create-missing-igroups
  "Create every group found in lgroups but not in igroups."
  [root options igroups lgroups]
  (logging/debug (type igroups))
  (logging/debug (type lgroups))
  (doseq [id (set/difference (-> lgroups keys set)
                             (-> igroups keys set))]
    (create-group (get lgroups id) options root)))


;### іgroups ##################################################################

(defn get-institutional-groups
  "Returns a map including all institutional groups. Every key is equal to the
  institutional_group_id of the group. Pipes the data through cheshire to json
  and back for consisten keyword encoding."
  [root]
  (->
    (->>
      (-> root
          (roa/relation :groups)
          (roa/get {})
          roa/coll-seq)
      (map #(roa/get % {}))
      (map roa/data)
      (filter #(= "InstitutionalGroup" (:type %)))
      (map (fn [g] [(:institutional_group_id g) g]))
      (into {}))
    cheshire/generate-string
    (cheshire/parse-string keyword)))

(defn run [ldap-data options]
  (logging/info "Running sync into Madek ....")
  (let [root (api-root options)]
    (check-connection! root)
    (let [igroups (get-institutional-groups root)
          lgroups (-> ldap-data :institutional-groups)]
      (create-missing-igroups root options igroups lgroups)
      ))
  (logging/info "Running sync into Madek done."))

;### Debug ####################################################################
;(debug/re-apply-last-argument #'run)
;(debug/debug-ns *ns*)
