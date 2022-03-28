(ns madek.sync.main
  (:refer-clojure :exclude [str keyword])
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :refer [parse-opts]]
    [logbug.catcher :as catcher]
    [logbug.thrown]
    [madek.sync.data-file :as data-file]
    [madek.sync.groups-sync :as groups-sync]
    [madek.sync.ldap-fetch :as ldap-fetch]
    [madek.sync.people-sync :as people-sync]
    [madek.sync.utils :refer [str keyword presence]]
    [taoensso.timbre :refer [debug info warn error spy]])
  (:gen-class))


(declare run)

;;; CLI ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def defaults
  {:MADEK_BASE_URL "http://localhost"
   :LDAP_BIND_DN "CN=madeksvc,OU=Service Accounts,OU=Accounts,OU=_ZHdK manuell,DC=ad,DC=zhdk,DC=ch"
   })

(defn env-or-default [kw & {:keys [parse-fn]
                            :or {parse-fn identity}}]
  (or (-> (System/getenv) (get (str kw) nil) presence)
      (get defaults kw nil)))

(def cli-options
  [["-h" "--help"]
   ["-t" "--madek-token MADEK_TOKEN"
    "Token used to authenticate against the Madek server."
    :default (env-or-default "MADEK_TOKEN")]
   ["-u" "--madek-base-url MADEK_BASE_URL"
    "Base URL of the Madek instance."
    :default (env-or-default :MADEK_BASE_URL)]
   [nil "--skip-create-groups" "Skips creating new groups" :default false]
   [nil "--skip-update-groups" "Skips updating new groups" :default false]
   [nil "--delete-groups" "Delete institutional-groups found in Madek but not in LDAP" :default false]
   [nil "--skip-create-people" "Skips creating new people" :default false]
   [nil "--skip-update-people" "Skips updating new people" :default false]
   [nil "--delete-people" "Delete institutional-people found in Madek but not in LDAP" :default false]
   [nil "--input-file INPUT_FILE"
    "The data will be retrieved from this file instead of fetching it from LDAP"
    :default (System/getenv "INPUT_FILE")]
   [nil "--output-file OUTPUT_FILE"
    "The data to be synced will be written to this json file instead."
    :default (System/getenv "OUTPUT_FILE")]
   [nil "--ldap-host" "Hostname/ip of the LDAP server" :default "ldaps.zhdk.ch"]
   [nil "--ldap-bind-dn LDAP_BIND_DN"
    :default (env-or-default :LDAP_BIND_DN)]
   [nil "--ldap-password LDAP_PASSWORD"
    "Password used to bind against the LDAP server."
    :default (System/getenv "LDAP_PASSWORD")]
   ])

(defn usage [options-summary & more]
  (->> ["Madek ZHdK LDAP Sync"
        ""
        "usage: madek-ldap-sync [<opts>]"
        ""
        "Options:"
        options-summary
        ""
        ""
        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (clojure.string/join \newline)))



(defn -main [& args]
  (logbug.thrown/reset-ns-filter-regex #"^(madek\.)|(.*roa.*).*")
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options :in-order true)]
    (cond
      (:help options) (println (usage summary {:options options}))
      :else (run options)
      )))

;(-main "-h")
;(-main)
;(-main "--input-file" "tmp/data_2017-11.json" "--madek-base-url" "http://localhost:3100")

;;; RUN ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn run [options]
  (catcher/snatch
    {:return-fn (fn [_] (System/exit -1))}
    (info "Madek LDAP Sync ....")
    (let [data (if (:input-file options)
                 (data-file/run-read options)
                 (ldap-fetch/run options))]
      (if (:output-file options)
        (data-file/run-write data options)
        (do (groups-sync/run data options)
            (people-sync/run data options))))
    (info "Madek LDAP Sync done."))
  (System/exit 0))


