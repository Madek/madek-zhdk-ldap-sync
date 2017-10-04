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
                      roa/request)]
    (logging/debug 'auth-info auth-info)))


;### іgroups ##################################################################

(defn get-institutional-groups
  "Returns a map including all institutional groups. Every key is equal to the
  institutional_id of the group. Pipes the data through cheshire to json
  and back for consisten keyword encoding."
  [root]
  (->
    (->>
      (-> root
          (roa/relation :groups)
          roa/request
          roa/coll-seq)
      (map roa/request)
      (map roa/data)
      (filter #(= "InstitutionalGroup" (:type %)))
      (map (fn [g] [(:institutional_id g) g]))
      (into {}))
    cheshire/generate-string
    (cheshire/parse-string keyword)))


;### create new groups ########################################################

(defn create-group [data options root]
  (let [id (-> data :institutional_id str)
        data (assoc data :type "InstitutionalGroup") ]
    (logging/info "Creating group " id " with data " (cheshire/generate-string data))
    (-> root
        (roa/relation :groups)
        (roa/request {} :post
                     {:headers {"Content-Type" "application/json"}
                      :body (cheshire/generate-string data)}))))

(defn create-missing-igroups
  "Create every group found in lgroups but not in igroups."
  [root options lgroups]
  (let [igroups (get-institutional-groups root)]
    (doseq [id (set/difference (-> lgroups keys set)
                               (-> igroups keys set))]
      (create-group (get lgroups id) options root))))


;### update groups ############################################################

(defn update-group [id params root]
  (-> root
      (roa/relation :group)
      (roa/request {:id id}
                   :patch
                   {:headers {"Content-Type" "application/json"}
                    :body (cheshire/generate-string params)})))

(defn update-groups
  "Update every group in the intersection of ldap and institutional
  madek groups where the parameters are not equal."
  [root options lgroups]
  (let [igroups (get-institutional-groups root)]
    (doseq [id (set/intersection (-> igroups keys set)
                                 (-> lgroups keys set))]
      (let [lgroup (get lgroups id)
            igroup (get igroups id)
            lgroup-params (select-keys lgroup [:name :institutional_name])
             igroup-params (select-keys igroup  [:name :institutional_name]) ]
        (when (not= lgroup-params igroup-params)
          (logging/info "Updating group " (str id) " " (cheshire/generate-string igroup-params)
                        " -> "(cheshire/generate-string lgroup-params))
          (update-group (str id) lgroup-params root))))))

;### delete groups ############################################################

(defn delete-group [id root]
  (-> root
      (roa/relation :group)
      (roa/request {:id id} :delete)))

(defn delete-groups
  "Delete every institutional-group found in Madek but not in LDAP"
  [root options lgroups]
  (let [igroups (get-institutional-groups root)]
    (doseq [id (set/difference (-> igroups keys set)
                               (-> lgroups keys set))]
      (logging/info "Deleting group " (cheshire/generate-string (get igroups id)))
      (delete-group (str id) root))))


;### run ######################################################################

(defn run [ldap-data options]
  (logging/info "Running sync into Madek ....")
  (let [root (api-root options)]
    (check-connection! root)
    (let [ lgroups (-> ldap-data :institutional-groups)]
      (when-not (:skip-create options)
        (create-missing-igroups root options lgroups))
      (when-not (:skip-update options)
        (update-groups root options lgroups))
      (when (:delete options)
        (delete-groups root options lgroups))))
  (logging/info "Running sync into Madek done."))

;### Debug ####################################################################
;(debug/re-apply-last-argument #'run)
;(debug/debug-ns *ns*)
