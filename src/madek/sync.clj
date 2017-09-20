(ns madek.sync
  (:require
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
    (logging/info 'auth-info auth-info)))

(defn institutional-groups
  "Returns a map including all institutional groups. Every key is equal to
  the institutional_group_id of the group."
  [root]
  (->> (-> root
           (roa/relation :groups)
           (roa/get {})
           roa/coll-seq)
       (map #(roa/get % {}))
       (map roa/data)
       (filter #(= "InstitutionalGroup" (:type %)))
       (map (fn [g] [(:institutional_group_id g) g]))
       (into {})))

(defn run [ldap-groups options]
  (logging/info "Running sync into Madek ....")
  (let [root (api-root options)]
    (check-connection! root)
    (let [igroups (institutional-groups root)]
      ))
  (logging/info "Running sync into Madek done."))

;### Debug ####################################################################
;(debug/re-apply-last-argument #'run)
(debug/debug-ns *ns*)
