(ns madek.main
  (:refer-clojure :exclude [str keyword])

  (:require
    [madek.data-file :as data-file]
    [madek.groups-sync :as groups-sync]
    [madek.ldap-fetch :as ldap-fetch]
    [madek.people-sync :as people-sync]
    [madek.utils :refer [str keyword]]


    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :refer [parse-opts]]

    [logbug.thrown]
    [logbug.catcher :as catcher]
    [clojure.tools.logging :as logging]
    )
  (:gen-class))


(declare run)

;;; CLI ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def cli-options
  [["-h" "--help"]
   ["-t" "--madek-token MADEK_TOKEN" "Token used to authenticate against the Madek server." :default (System/getenv "MADEK_TOKEN")]
   ["-u" "--madek-base-url BASE_URL" "Base URL of the Madek instance." :default "https://test.madek.zhdk.ch"]
   [nil "--skip-create-groups" "Skips creating new groups" :default false]
   [nil "--skip-update-groups" "Skips updating new groups" :default false]
   [nil "--delete-groups" "Delete institutional-groups found in Madek but not in LDAP" :default false]
   [nil "--skip-create-people" "Skips creating new people" :default false]
   [nil "--skip-update-people" "Skips updating new people" :default false]
   [nil "--delete-people" "Delete institutional-people found in Madek but not in LDAP" :default false]
   [nil "--input-file INOUT_FILE" "The data will be retrieved from this file instead of fetching it from LDAP"]
   [nil "--output-file OUTPUT_FILE" "The data to be synced will be written to this json file instead." :default (System/getenv "OUTPUT_FILE")]
   [nil "--ldap-host" "Hostname/ip of the LDAP server" :default "adc3.ad.zhdk.ch"]
   [nil "--ldap-bind-dn BIND_DN" :default "CN=madeksvc,OU=Service Accounts,OU=Accounts,OU=_ZHdK manuell,DC=ad,DC=zhdk,DC=ch"]
   [nil "--ldap-password LDAP_PASSWORD" "Password used to bind against the LDAP server." :default (System/getenv "LDAP_PASSWORD")]
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
;(-main "--input-file tmp/data.json" "--madek-base-url" "http://localhost:3100")

;;; RUN ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn run [options]
  (catcher/snatch
    {:return-fn (fn [_] (System/exit 0))}
    (logging/info "Madek LDAP Sync ....")
    (let [data (if (:input-file options)
                 (data-file/run-read options)
                 (ldap-fetch/run options))]
      (if (:output-file options)
        (data-file/run-write data options)
        (do (groups-sync/run data options)
            (people-sync/run data options))))
    (logging/info "Madek LDAP Sync done.")))


