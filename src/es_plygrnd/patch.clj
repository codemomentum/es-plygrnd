(ns es-plygrnd.patch
  (:require [clj-http.client :as http])
  )


(defn wrapex
  [client]
  (fn [req]
    (let [{:keys [status] :as resp} (client req)]
      (println req)
      (if (clj-http.client/unexceptional-status? status)
        resp
        (slingshot.slingshot/throw+ resp "clj-http: STATUS %d %s" (:status %) resp)))))

(def middleware
  "The default list of middleware clj-http uses for wrapping requests."
  [http/wrap-request-timing
   clj-http.headers/wrap-header-map
   http/wrap-query-params
   http/wrap-basic-auth
   http/wrap-oauth
   http/wrap-user-info
   http/wrap-url
   http/wrap-redirects
   http/wrap-decompression
   http/wrap-input-coercion
   ;; put this before output-coercion, so additional charset
   ;; headers can be used if desired
   http/wrap-additional-header-parsing
   http/wrap-output-coercion
   wrapex
   http/wrap-accept
   http/wrap-accept-encoding
   http/wrap-content-type
   http/wrap-form-params
   http/wrap-nested-params
   http/wrap-method
   http/wrap-unknown-host])

;(defn myanalyze
;  ([conn text & args]
;   (let [opts (clojurewerkz.elastisch.arguments/->opts args)
;         url (esr/analyze-url conn
;                              (:index opts))
;         query {:query-params (assoc opts :text text)}]
;     (println url)
;     (println query)
;     (http/with-middleware middleware
;                           (esr/get conn
;                                    url
;                                    query)))))