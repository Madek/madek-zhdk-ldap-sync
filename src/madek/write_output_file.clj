(ns madek.write-output-file
  (:require
    [cheshire.core :as cheshire]

    [clojure.tools.logging :as logging]
    ))


(defn run [data options]
  (spit (:output-file options)
        (cheshire/generate-string data {:pretty true})))
