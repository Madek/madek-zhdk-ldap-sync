(ns madek.sync.people-sync
  (:refer-clojure :exclude [str keyword])
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as http-client]
   [clojure.set :as set]
   [madek.sync.utils :refer [keyword str]]
   [taoensso.timbre :refer [debug info]]))

(defn get-madek-api-config
  "Extracts config for Madek API from the options map"
  [{:keys [madek-base-url madek-token]}]
  {:base-url (str madek-base-url "/api-v2/")
   :auth-header (str "token " madek-token)})

(defn check-connection!
  "Retrieves the auth-info relation and causes an error to be thrown
   if there is an authentication problem."
  [{:keys [base-url auth-header]}]
  (let [url (str base-url "auth-info/")
        auth-info (http-client/get url {:as :json
                                        :headers {"Authorization" auth-header}})]
    (debug 'auth-info auth-info)))


;### іpeople ##################################################################

(defn get-institutional-people
  "Returns a map including all institutional people. Every key is equal to the
  institutional_id of the person. Pipes the data through cheshire to json
  and back for consistent keyword encoding."
  [{:keys [base-url auth-header]}]
  (let [url (str base-url
                 "admin/people/?"
                 (http-client/generate-query-string {:subtype "PeopleInstitutionalGroup"}))]
    (debug "fetching" url)
    (->
     (->>
      (http-client/get url {:as :json
                            :headers {"Authorization" auth-header}})
      :body
      :people
      (map (fn [g] [(:institutional_id g) g]))
      (into {}))
     cheshire/generate-string
     (cheshire/parse-string keyword))))


;### create new people ########################################################

(defn create-person [{:keys [base-url auth-header]} ldata]
  (let [data (assoc ldata :subtype "PeopleInstitutionalGroup")
        url (str base-url "admin/people/")]
    (info "POST" url)
    (-> (http-client/post url {:as :json
                               :content-type :json
                               :form-params data
                               :headers {"Authorization" auth-header}}))))

(defn create-people
  "Create every person found in lpeople but not in ipeople."
  [madek-api-config lpeople ipeople]
  (doseq [id (set/difference (-> lpeople keys set)
                             (-> ipeople keys set))]
    (let [ldata (get lpeople id)]
      (info "Creating person with data " (cheshire/generate-string ldata))
      (create-person madek-api-config ldata))))


;### update people ############################################################

(defn update-person
  [{:keys [base-url auth-header]} id data]
  (let [url (str base-url "admin/people/" id)]
    (info "PATCH" url)
    (-> (http-client/patch url {:as :json
                                :content-type :json
                                :form-params data
                                :headers {"Authorization" auth-header}})
        :body)))

(defn update-people
  "Update every person in the intersection of ldap and institutional
  madek people where the parameters are not equal."
  [madek-api-config lpeople ipeople]
  (doseq [id (set/intersection (-> ipeople keys set)
                               (-> lpeople keys set))]
    (let [lperson (get lpeople id)
          iperson (get ipeople id)
          lperson-params (select-keys lperson [:last_name :pseudonym])
          iperson-params (select-keys iperson  [:last_name :pseudonym])]
      (when (not= lperson-params iperson-params)
        (info "Updating person" (:id iperson) (cheshire/generate-string iperson-params)
              "->" (cheshire/generate-string lperson-params))
        (update-person madek-api-config (:id iperson) lperson-params)))))


;### delete people ############################################################

(defn delete
  [{:keys [base-url auth-header]} id]
  (let [url (str base-url "admin/people/" id)]
    (info "DELETE" url)
    (-> (http-client/delete url {:as :json
                                 :headers {"Authorization" auth-header}})
        :body)))

(defn delete-people
  "Delete every institutional-person found in Madek but not in LDAP"
  [madek-api-config lpeople ipeople]
  (doseq [id (set/difference (-> ipeople keys set)
                             (-> lpeople keys set))]
    (let [iperson (get ipeople id)]
      (info "Deleting person " (cheshire/generate-string iperson))
      (delete madek-api-config (:id iperson)))))


;### prepare ldap-data #########################################################

(def key-property-mapping
  {:name :last_name
   :institutional_name :pseudonym
   :institutional_id :institutional_id})

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
  (let [config (get-madek-api-config options)]
    (check-connection! config)
    (let [lpeople (-> ldap-data prepare-ldap-data)
          ipeople (get-institutional-people config)]
      (info "Current number of groups in LDAP:" (count lpeople))
      (info "Current number of 'PeopleInstitutionalGroup's in Madek:" (count ipeople))
      (if (:skip-create-people options)
        (info "Skipping create")
        (create-people config lpeople ipeople))
      (if (:skip-update-people options)
        (info "Skipping update")
        (update-people config lpeople ipeople))
      (if (:delete-people options)
        (delete-people config lpeople ipeople)
        (info "Skipping delete"))))
  (info "sync people done. <<<<<<<<<<<<<<<<<<<<<<<<<"))

;### Debug ####################################################################
;(debug/re-apply-last-argument #'run)
;(debug/debug-ns *ns*)
