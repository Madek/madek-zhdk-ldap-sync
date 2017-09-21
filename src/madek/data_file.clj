(ns madek.data-file
  (:refer-clojure :exclude [str keyword])

  (:require
    [madek.utils :refer [str keyword]]

    [cheshire.core :as cheshire]

    [clojure.tools.logging :as logging]
    ))

(defn run-write [data options]
  (let [filename (:output-file options)]
    (logging/info (str "Writing data to " filename " ...."))
    (spit filename (cheshire/generate-string data {:pretty true}))
    (logging/info (str "Writing data to " filename " done."))))

(defn run-read [options]
  (let [filename (:input-file options)]
    (logging/info (str "Reading data from " filename " ...."))
    (let [data (-> (slurp filename)
                   (cheshire/parse-string true))]
      (logging/info (str "Reading data from " filename " done."))
      data)))
