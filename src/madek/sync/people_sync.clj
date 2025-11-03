(ns madek.sync.people-sync
  (:refer-clojure :exclude [str keyword])
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as http-client]
   [clojure.set :as set]
   [json-roa.client.core :as roa]
   [madek.sync.utils :refer [str keyword presence]]
   [taoensso.timbre :refer [debug info warn error spy]]))

(def key-property-mapping
  {:name :last_name
   :institutional_name :pseudonym
   :institutional_id :institutional_id})

(defn get-madek-api-config [{base-url :madek-base-url token :madek-token}]
  {:base-url (str base-url "/api-v2/")
   :auth-header (str "token " token)})

(defn check-connection!
  "Retrieves the auth-info relation and causes an error to be thrown
   if there is an authentication problem."
  [{:keys [base-url auth-header]}]
  (let [url (str base-url "auth-info/")]
    (debug "check connection by fetching" url)
    (let [auth-info (http-client/get url {:as :json
                                          :headers {"Authorization" auth-header}})]
      (debug "connection ok")
      (debug 'auth-info auth-info))))


;### іpeople ##################################################################

(defn get-institutional-people
  "Returns a map including all institutional people. Every key is equal to the
    institutional_id of the person. Pipes the data through cheshire to json
    and back for consisten keyword encoding."
  [{:keys [base-url auth-header]}]
  (let [url (str base-url
                 "admin/people/?"
                 (http-client/generate-query-string {:subtype "PeopleInstitutionalGroup"}))]
    (debug "fetching" url)
    (->
     (->>
      (http-client/get url {:as :json
                            :headers {"Authorization" auth-header}})
      :body :people
      (map (fn [g] [(:institutional_id g) g]))
      (into {}))
     cheshire/generate-string
     (cheshire/parse-string keyword))))

;### create new people ########################################################

(defn post
  [{:keys [base-url auth-header]} data]
  (let [url (str base-url "admin/people/")]
    (info "posting" url data)
    (-> (http-client/post url {:as :json
                               :content-type :json
                               :form-params data
                               :headers {"Authorization" auth-header}}))))

(defn create-person [request-config ldata]
  (let [id (-> ldata :institutional_id str)
        data (assoc ldata :subtype "PeopleInstitutionalGroup")]
    (info "Creating person " id " with data " (cheshire/generate-string data))
    (post request-config data)))

(defn create-missing-ipeople
  "Create every person found in lpeople but not in ipeople."
  [request-config lpeople]
  (let [ipeople (get-institutional-people request-config)]
    (doseq [id (set/difference (-> lpeople keys set)
                               (-> ipeople keys set))]
      (create-person request-config (get lpeople id)))))


;### update people ############################################################

(defn patch
  [{:keys [base-url auth-header]} madek-id data]
  (assert (presence madek-id))
  (let [url (str base-url "admin/people/" madek-id)]
    (info "patching" url data)
    (-> (http-client/patch url {:as :json
                                :content-type :json
                                :form-params data
                                :headers {"Authorization" auth-header}})
        :body)))

(defn update-people
  "Update every person in the intersection of ldap and institutional
  madek people where the parameters are not equal."
  [request-config lpeople]
  (let [ipeople (get-institutional-people request-config)]
    (info "Found" (count ipeople) " institutional people")
    (doseq [id (set/intersection (-> ipeople keys set)
                                 (-> lpeople keys set))]
      (let [lperson (get lpeople id)
            iperson (get ipeople id)
            lperson-params (select-keys lperson [:last_name :pseudonym])
            iperson-params (select-keys iperson  [:last_name :pseudonym])]
        (when (not= lperson-params iperson-params)
          (info "Updating person" (:id iperson) (cheshire/generate-string iperson-params)
                "->" (cheshire/generate-string lperson-params))
          (patch request-config (:id iperson) lperson-params))))))

;### delete people ############################################################

(defn delete
  [{:keys [base-url auth-header]} madek-id]
  (assert (presence madek-id))
  (let [url (str base-url "admin/people/" madek-id)]
    (info "deleting" url)
    (-> (http-client/delete url {:as :json
                                 :headers {"Authorization" auth-header}})
        :body)))

(defn delete-people
  "Delete every institutional-person found in Madek but not in LDAP"
  [request-config lpeople]
  (let [ipeople (get-institutional-people request-config)]
    (doseq [id (set/difference (-> ipeople keys set)
                               (-> lpeople keys set))]
      (let [iperson (get ipeople id)]
        (info "Deleting person " (cheshire/generate-string iperson))
        (delete request-config (:id iperson))))))


;### prepare ldap-data #########################################################

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
  (let [madek-api-config (get-madek-api-config options)]
    (check-connection! madek-api-config)
    (let [lpeople (-> ldap-data prepare-ldap-data)]
      (info "LDAP has" (count lpeople) "people")
      (when-not (:skip-create-people options)
        (create-missing-ipeople madek-api-config lpeople))
      (when-not (:skip-update-people options)
        (update-people madek-api-config lpeople))
      (when (:delete-people options)
        (delete-people madek-api-config lpeople))))
  (info "sync people done. <<<<<<<<<<<<<<<<<<<<<<<<<"))

;### Debug ####################################################################
;(debug/re-apply-last-argument #'run)
;(debug/debug-ns *ns*)
