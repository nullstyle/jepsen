(ns jepsen.faunadb.g2
  "Tests for anti-dependency cycles"
  (:refer-clojure :exclude [test])
  (:import com.faunadb.client.errors.UnavailableException)
  (:import java.io.IOException)
  (:require [jepsen [client :as client]
                    [checker :as checker]
                    [core :as jepsen]
                    [fauna :as fauna]
                    [util :as util :refer [meh]]
                    [generator :as gen]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.tests.adya :as adya]
            [jepsen.faunadb [client :as f]
                            [query :as q]]
            [dom-top.core :as dt]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.tools.logging :refer :all]
            [knossos.op :as op]))

; Two classes
(def a-name "a")
(def a (q/class a-name))
(def b-name "b")
(def b (q/class b-name))

; And an index on each
(def a-index-name "a-index")
(def a-index (q/index a-index-name))
(def b-index-name "b-index")
(def b-index (q/index b-index-name))

(defrecord G2Client [conn]
  client/Client
  (open! [this test node]
    (assoc this :conn (f/client node)))

  (setup! [this test]
    (f/with-retry
      (f/query conn (q/do (f/upsert-class {:name a-name})
                          (f/upsert-class {:name b-name})))
      (f/query conn (q/do (f/upsert-index
                            {:name a-index-name
                             :source a
                             :serialized (boolean (:serialized-indices test))
                             :terms [{:field ["data" "key"]}]})
                          (f/upsert-index
                            {:name b-index-name
                             :source b
                             :serialized (boolean (:serialized-indices test))
                             :terms [{:field ["data" "key"]}]})))
      (f/wait-for-index conn a-index)
      (f/wait-for-index conn b-index)))

  (invoke! [this test op]
    (assert (= :insert (:f op)))
    (f/with-errors op #{}
      (let [[k [a-id b-id]] (:value op)
            id (or a-id b-id)
            class (if a-id a b)             ; Class we insert to
            index (if a-id b-index a-index) ; Index we check for conflict
            res (f/query conn (q/when (q/not (q/exists? (q/match index k)))
                                (q/create (q/ref class id)
                                          {:data {:key k}})))]
        (assoc op :type (if res :ok :fail)))))

  (teardown! [this test])

  (close! [this test]
    (.close conn)))

(defn test
  [opts]
  (fauna/basic-test
    (merge
      {:name "set"
       :client {:client (G2Client. nil)
                :during (adya/g2-gen)}
       :checker (checker/compose
                  {:perf (checker/perf)
                   :g2 (adya/g2-checker)})}
      opts)))
