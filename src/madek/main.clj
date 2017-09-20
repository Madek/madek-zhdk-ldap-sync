(ns madek.main
  (:require
    [madek.ldap-fetch :as ldap-fetch]

    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))


(declare run)




;;; CLI ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def cli-options
  [["-h" "--help"]
   ["-t" "--madek-token MADEK_TOKEN" "Token used to authenticate against the Madek server." :default (System/getenv "MADEK_TOKEN")]
   ["-u" "--madek-base-url BASE_URL" "Base URL of the Madek instance." :default "https://test.madek.zhdk.ch"]
   [nil "--output-file OUTPUT_FILE" "The data to be synced will be written to this json file instead." :default (System/getenv "OUTPUT_FILE")]
   [nil "--ldap-host" "Hostname/ip of the LDAP server" :default "adc3.ad.zhdk.ch"]
   [nil "--ldap-bind-dn BIND-DN" :default "CN=madeksvc,OU=Service Accounts,OU=Accounts,OU=_ZHdK manuell,DC=ad,DC=zhdk,DC=ch"]
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
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options :in-order true)]
    (cond
      (:help options) (println (usage summary {:options options}))
      :else (run options)
      )))

;(-main "-h")

;(-main)

;;; RUN ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn run [options]
  (println "Running Madek LDAP Sync ....")
  (ldap-fetch/run options)
  (println "Running Madek LDAP Sync done.")
  )
