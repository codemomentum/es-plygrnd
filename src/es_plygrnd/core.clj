(ns es-plygrnd.core
  (:require [clj-http.client :as http]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [clojure.pprint :as pp]
            [es-plygrnd.patch :as patch]))

(def index "test")
(def itype "syslog")

(def mydoc {:ip     "8.8.8.8"
            :reason "The client's IP address is invalid because of ACL constraint."
            :msg    "[illidan.stormrage]: User illidan.stormrage from 9.9.9.9 logged in"})

(def conn (esr/connect "http://127.0.0.1:9200"))

(defn qp
  ;query and print
  [c i t q]
  (http/with-middleware patch/middleware
                        (let [res (esd/search c i t :query q)
                              n (esrsp/total-hits res)
                              hits (esrsp/hits-from res)]
                          (println (format "Total hits: %d" n))
                          (pp/pprint hits))))



(esi/delete conn index)
;V1
(println (esd/create conn index itype mydoc))

;QUERIES
;http://clojureelasticsearch.info/articles/querying.html

;(println (esi/get-mapping conn index type))
(qp conn index itype (q/match-all))


;The Term query is the most basic query type. It matches documents that have a particular term.
; A common
; use case for term queries is looking up documents with unanalyzed identifiers such as usernames.
(qp conn index itype (q/term :ip "8.8.8.8"))
(qp conn index itype (q/term :ip "8.8.8"))
(qp conn index itype (q/term :msg "illidan.stormrage"))
(qp conn index itype (q/term :msg "illidan.stormragex"))
(qp conn index itype (q/term :msg "9.9.9.9"))
(qp conn index itype (q/term :msg "9.9.9"))
(qp conn index itype (q/term :msg "in"))

;CANNOT SEARCH BY IP PREFIX
;delete and reindex
(esi/delete conn index)
;http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/mapping-ip-type.html
;V2
(esi/create conn index :mappings {"syslog" {:properties
                                             {
                                               :ip     {:type "ip" :store "yes"}
                                               :reason {:type "string" :store "yes"}
                                               :msg    {:type "string" :store "yes"}
                                             }
                                           }})

(println (esd/create conn index itype mydoc))

(qp conn index itype (q/term :ip "8.8.8.8"))
(qp conn index itype (q/term :ip "8.8.8.*"))                ;expected to fail - use range

;Range query:returns documents with fields that have numerical values, dates or terms within a specific range.
(qp conn index itype {:range {:ip
                               {:from "8.8.8.0" :to "8.8.8.255" :include_lower true :include_upper false}}})

(qp conn index itype {:range {:ip
                               {:from "0.0.0.0" :to "255.255.255.255" :include_lower true :include_upper false}}})

;IP QUERY OK



;Query string query
;pass

;Boolean query:A query that matches documents matching boolean combinations of other queries.
(qp conn index itype {:bool {:must   [{:term {:msg "illidan.stormrage"}}]
                             :should [{:range {:ip {:from "8.8.8.0"}}}]}}
    )


;Filtered Query
;A query that applies a filter to the results of another query. Use it if you need to narrow down results
; of an existing query efficiently but the condition you filter on does not affect relevance ranking.


(println (esd/create conn index itype mydoc))



;FULL TEXT SEARCH AND IP RANGE SEARCH
;http://www.elasticsearch.org/guide/en/elasticsearch/guide/current/configuring-analyzers.html

(pp/pprint (esd/analyze conn (:msg mydoc)))
(esi/delete conn index)


;V3
(http/with-middleware
  patch/middleware
  (esi/create conn index
              :settings {:index {"analysis" {"analyzer"
                                              {"my_analyzer"
                                                {"alias"     "my_analyzer1"
                                                 "type"      "custom"
                                                 "tokenizer" "standard"
                                                 "filter"    ["standard", "lowercase", "stop", "word_delimiter"]
                                                 "stopwords" ["in"]}}

                                            }}}
              :mappings {"syslog" {:properties
                                    {
                                      :ip     {:type "ip" :store "yes"}
                                      :reason {:type "string" :store "yes"}
                                      :msg    {:type     "string" :store "yes"
                                               :analyzer "my_analyzer"}
                                    }}}))

(pp/pprint (esi/get-mapping conn index itype))
(pp/pprint (esd/create conn index itype mydoc))

(pp/pprint (esd/analyze conn (:msg mydoc) :tokenizer "standard" :token_filters ["word_delimiter"]))

(qp conn index itype (q/term :ip "8.8.8.8"))
(qp conn index itype (q/term :msg "illidan.stormrage"))     ;this doesnt work now
(qp conn index itype (q/term :msg "illidan"))
(qp conn index itype (q/term :msg "stormrage"))
(qp conn index itype (q/term :msg "9.9.9.9"))               ;this doesnt work now
(qp conn index itype (q/term :msg "in"))


(esi/delete conn index)
;V4
(http/with-middleware
  patch/middleware
  (esi/create conn index
              :settings {:index {"analysis" {"analyzer"
                                              {"my_analyzer"
                                                {"alias"     "my_analyzer1"
                                                 "type"      "custom"
                                                 "tokenizer" "standard"
                                                 "filter"    ["standard", "lowercase", "stop", "word_delimiter"]
                                                 "stopwords" ["in"]}}

                                            }}}
              :mappings {"syslog" {:properties
                                    {
                                      :ip     {:type "ip"}
                                      :reason {:type "string"}
                                      :msg    {:type   "multi_field"
                                               :fields {:orjmsg {:type "string"}
                                                        :modmsg {:type "string" :analyzer "my_analyzer"}}
                                              }
                                    }}}))


(pp/pprint (esd/create conn index itype mydoc))

(qp conn index itype (q/term :ip "8.8.8.8"))
(qp conn index itype (q/term :orjmsg "illidan.stormrage"))
(qp conn index itype (q/term :modmsg "illidan"))
(qp conn index itype (q/term :modmsg "stormrage"))
(qp conn index itype (q/term :orjmsg "9.9.9.9"))

;multi-match
(qp conn index itype {:multi_match {:query "illidan" :fields [:orjmsg :modmsg]}})
;fuzzy
(qp conn index itype {:fuzzy {:modmsg "illidan"}})
(qp conn index itype {:fuzzy {:modmsg "illidun"}})
(qp conn index itype {:fuzzy {:modmsg "ullidan"}})
;great but no similarity (typo etc..) at all?


(esi/delete conn index)
;V5


;TODO
;custom filter and tokenizers and concepts
;caching samples
;facet->aggregation, subdocument aggregations
;suggest












