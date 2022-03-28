(ns madek.sync.people-sync
  (:refer-clojure :exclude [str keyword])
  (:require
    [cheshire.core :as cheshire]
    [clojure.pprint :refer [pprint]]
    [clojure.set :as set]
    [json-roa.client.core :as roa]
    [logbug.debug :as debug]
    [madek.sync.utils :refer [str keyword]]
    [taoensso.timbre :refer [debug info warn error spy]]))

(def key-property-mapping
  {:name :last_name
   :institutional_name :pseudonym
   :institutional_id :institutional_id})

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
    (debug 'auth-info auth-info)))


;### іpeople ##################################################################

(defn get-institutional-people
  "Returns a map including all institutional people. Every key is equal to the
  institutional_id of the person. Pipes the data through cheshire to json
  and back for consisten keyword encoding."
  [root]
  (->
    (->>
      (-> root
          (roa/relation :people)
          roa/request
          roa/coll-seq)
      (map roa/request)
      (map roa/data)
      (filter #(= "PeopleInstitutionalGroup" (:subtype %)))
      (map (fn [g] [(:institutional_id g) g]))
      (into {}))
    cheshire/generate-string
    (cheshire/parse-string keyword)))


;### create new people ########################################################

(defn create-person [data options root]
  (let [id (-> data :institutional_id str)
        data (assoc data :subtype "PeopleInstitutionalGroup") ]
    (info "Creating person " id " with data " (cheshire/generate-string data))
    (-> root
        (roa/relation :people)
        (roa/request {} :post
                     {:headers {"Content-Type" "application/json"}
                      :body (cheshire/generate-string data)}))))

(defn create-missing-ipeople
  "Create every person found in lpeople but not in ipeople."
  [root options lpeople]
  (let [ipeople (get-institutional-people root)]
    (doseq [id (set/difference (-> lpeople keys set)
                               (-> ipeople keys set))]
      (create-person (get lpeople id) options root))))


;### update people ############################################################

(defn update-person [id params root]
  (-> root
      (roa/relation :person)
      (roa/request {:id id}
                   :patch
                   {:headers {"Content-Type" "application/json"}
                    :body (cheshire/generate-string params)})))

(defn update-people
  "Update every person in the intersection of ldap and institutional
  madek people where the parameters are not equal."
  [root options lpeople]
  (let [ipeople (get-institutional-people root)]
    (doseq [id (set/intersection (-> ipeople keys set)
                                 (-> lpeople keys set))]
      (let [lperson (get lpeople id)
            iperson (get ipeople id)
            lperson-params (select-keys lperson [:last_name :pseudonym])
            iperson-params (select-keys iperson  [:last_name :pseudonym])]
        (when (not= lperson-params iperson-params)
          (info "Updating person " (str id) " " (cheshire/generate-string iperson-params)
                        " -> "(cheshire/generate-string lperson-params))
          (update-person (str id) lperson-params root))))))

;### delete people ############################################################

(defn delete-person [id root]
  (-> root
      (roa/relation :person)
      (roa/request {:id id} :delete)))

(defn delete-people
  "Delete every institutional-person found in Madek but not in LDAP"
  [root options lpeople]
  (let [ipeople (get-institutional-people root)]
    (doseq [id (set/difference (-> ipeople keys set)
                               (-> lpeople keys set))]
      (info "Deleting person " (cheshire/generate-string (get ipeople id)))
      (delete-person (str id) root))))


;### prepare dap-data #########################################################

(defn map-group [grp]
  (->> grp
       (map (fn [[k v]] [(get key-property-mapping k) v]))
       (into {})))

(defn group-filter [grp]
  (->> grp
       second
       :institutional_id
       (re-matches #"^.*\.alle$")))

(defn prepare-ldap-data [data]
  (->> data
       :institutional-groups
       (filter group-filter)
       (map (fn [[id grp]] [id (map-group grp)]))
       (into {})))


;### run ######################################################################

(defn run [ldap-data options]
  (info ">>>>>>>>>>>>>>>>>>>>> Syncing people ....")
  (let [root (api-root options)]
    (check-connection! root)
    (let [lpeople (-> ldap-data prepare-ldap-data)]
      (when-not (:skip-create-people options)
        (create-missing-ipeople root options lpeople))
      (when-not (:skip-update-people options)
        (update-people root options lpeople))
      (when (:delete-people options)
        (delete-people root options lpeople))))
  (info "sync people done. <<<<<<<<<<<<<<<<<<<<<<<<<"))

;### Debug ####################################################################
;(debug/re-apply-last-argument #'run)
;(debug/debug-ns *ns*)
