(defproject madek-ldap-sync "0.1.0"
  :description "Sync groups from ZHdK AD/LDAP into Madek"
  :url "https://github.com/Madek/madek-ldap-sync"
  :license {:name "WTFPL"
            :url "http://www.wtfpl.net/txt/copying/"}
  :dependencies [
                 [cheshire "5.7.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [logbug "4.2.2"]
                 [org.clojars.pntblnk/clj-ldap "0.0.12"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.slf4j/slf4j-log4j12 "1.7.25"]
                 ]
  :main ^:skip-aot madek.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
