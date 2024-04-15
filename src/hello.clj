(ns hello
  (:require [clojure.data.json :as json]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.content-negotiation :as content-negotiation]))

(defn ok [body]
  {:status 200 :body body
   :headers {"Content-Type" "text/html"}})

(defn not-found []
  {:status 404 :body "Not found\n"})

(def unmentionables #{"Voldemort"
                      "  "})

(defn greeting-for [nm]
  (cond
    (unmentionables nm) nil
    (empty? nm) "Hello, world!\n"
    :else (str "Hello, " nm "\n")))

(defn respond-hello [request]
  (let [nm (get-in request [:query-params :name])
        resp (greeting-for nm)]
    (if resp
      (ok resp)
      (not-found))))

(def echo
  {:name ::echo
   :enter (fn [context]
            (let [request (:request context)
                  response (ok request)]
              (assoc context :response response)))})

(def supported-types ["text/html"
                      "application/edn"
                      "application/json"
                      "text/plain"])

(def content-negotiation-interceptor
  (content-negotiation/negotiate-content supported-types))

(def coerce-body-interceptor
  {:name ::coerce-body
   :leave
   (fn [context]
     (let [accepted (get-in context [:request :acceptor :field] "text/plain")
           response (get context :reponse)
           body (get response :body)
           coerced-body (case accepted
                          "text/html" body
                          "text/plain" body
                          "application/edn" (pr-str body)
                          "application/json" (json/write-str body))
           updated-response (assoc response
                                   :headers {"Content-Type" accepted}
                                   :body coerced-body)]
       (assoc context :response updated-response)))})

(def routes
  (route/expand-routes
   #{["/greet" :get [coerce-body-interceptor
                     content-negotiation-interceptor
                     respond-hello]
      :route-name :greet]
     ["/echo" :get echo]}))

(def service-map
  (http/create-server
   {::http/routes routes
    ::http/type :jetty
    ::http/port 8890}))

(defn start []
  (http/start (http/create-server service-map)))

;; For interactive development
(defonce server (atom nil))

(defn start-dev []
  (reset! server
          (http/start (http/create-server
                       (assoc service-map
                              ::http/join? false)))))

(defn stop-dev []
  (http/stop @server))

(defn restart []
  (stop-dev)
  (start-dev))
