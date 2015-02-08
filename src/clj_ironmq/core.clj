(ns clj-ironmq.core
  (:require [cheshire.core :as json]
            [clj-http.client :as http]))

(def aws-host "mq-aws-us-east-1.iron.io")
(def rackspace-host "mq-rackspace-dfw.iron.io")

(def ^:private config-keys
  [:project_id :token :scheme :host :port :api_version])

(def ^:private default-client-config
  {:scheme "https", :host aws-host, :port 443, :api_version 3})

(def ^:private default-http-options
  {:content-type :json, :accept :json, :as :json,
   :throw-exceptions false, :coerce :always})

;;; Generates names of configuration environment variables:
;;; {:project_id ["IRON_MQ_PROJECT_ID" "IRON_PROJECT_ID"], ...}
(defonce ^:private env-var-names
  (reduce (fn [m v]
            (let [vname (clojure.string/upper-case (name v))]
              (assoc m v [(str "IRON_MQ_" vname) (str "IRON_" vname)])))
          {}
          config-keys))

(defonce ^:private global-config-file-path
  (try
    (str (System/getenv "HOME") "/.iron.json")
    (catch Throwable _ nil)))

(defonce ^:private local-config-file-path
  (try
    (str (System/getProperty "user.dir") "/iron.json")
    (catch Throwable _ nil)))

(defn- env->config
  "Loads project ID and token from environment variables with precedence."
  []
  (let [env (try (System/getenv) (catch Throwable _ nil))]
    (reduce (fn [config [k names]]
              (if-let [value (reduce #(or %1 (get env %2)) nil names)]
                (assoc config k value)
                config))
            {}
            env-var-names)))

(defn file->config [path-to-file]
  {:pre [(seq path-to-file)]}
  (try
    (when-let [config (json/parse-string (slurp path-to-file) true)]
      (merge (select-keys config config-keys)
             (select-keys (:iron_mq config) config-keys)))
    (catch Throwable _ nil)))

(defn- files->config []
  (merge (file->config global-config-file-path)
         (file->config local-config-file-path)))

(defn- load-config-from-everywhere []
  (merge default-client-config (files->config) (env->config)))

(defn- process-entity [entity]
  (if (and (seq? entity)
           (not (string? entity)))
    (vec entity)
    entity))

(defn- get-opt [m & ks]
  (let [kwd-ks (mapv keyword ks)
        str-ks (mapv name ks)]
    (or (get-in m kwd-ks) (get-in m str-ks))))

(defn- make-request->response
  "Makes HTTP request to IronMQ endpoint, processes and returns response."
  [client params entity-key success-status]
  (try
    (let [resp (http/request (merge params (:http-options client)))
          _ (println resp)
          status (:status resp)
          entity (get-opt resp :body entity-key)]
      (if (and (= success-status status)
               (some? entity))
        (process-entity entity)
        {:status status, :msg (get-opt resp :body :msg)}))
    (catch Throwable ex {:status 0, :msg (.getMessage ex)})))

(defn- make-params [method url opts]
  (merge opts {:method method, :url url}))

(defn- make-url [base-url & path-parts]
  (clojure.string/join \/ (conj (filter some? path-parts) base-url)))

(defn- make-queues-url [client & path-parts]
  (apply make-url (conj path-parts (:base-url client))))

(defn- make-messages-url [client queue-name & path-parts]
  (apply (partial make-queues-url client queue-name)
         (conj path-parts "messages")))

(defn- make-body [value & [entity]]
  (if entity
    (json/generate-string {entity value})
    (json/generate-string value)))

(defn- get-ids [message] (select-keys message [:id :reservation_id]))

(defn- make-base-url
  [{:keys [scheme host port api_version project_id]}]
  (str scheme "://" host ":" port "/" api_version
       "/projects/" project_id "/queues"))

(defprotocol IronMQ
  (get-queues-list [this] [this options])
  (create-queue [this queue-name] [this queue-name options])
  (get-queue [this queue-name])
  (update-queue [this queue-name options])
  (delete-queue [this queue-name])
  (set-queue-subscribers [this queue-name subscribers])
  (replace-queue-subscribers [this queue-name subscribers])
  (delete-queue-subscribers [this queue-name subscribers])
  (post-messages [this queue-name messages])
  (post-message [this queue-name message])
  (make-reservations [this queue-name] [this queue-name options])
  (get-message-by-id [this queue-name message-id])
  (peek-messages [this queue-name] [this queue-name options])
  (touch-message
    [this queue-name message]
    [this queue-name message options]
    [this queue-name message-id reservation-id options])
  (release-message
    [this queue-name message]
    [this queue-name message options]
    [this queue-name message-id reservation-id options])
  (delete-message [this queue-name message])
  (delete-message-by-id
    [this queue-name message-id]
    [this queue-name message-id reservation-id])
  (delete-messages [this queue-name messages])
  (clear-queue [this queue-name])
  (get-message-push-statuses [this queue-name message-id]))

(defrecord Client [^String project-id
                   ^String token
                   ^String base-url
                   http-options]
  IronMQ
  ;; GET /queues
  (get-queues-list [this] (get-queues-list this {}))
  (get-queues-list [this options]
    (let [url (make-queues-url this)
          params (make-params :get url {:query-params options})]
      (make-request->response this params :queues 200)))
  ;; PUT /queues/{queue-name}
  (create-queue [this queue-name] (create-queue this queue-name {}))
  (create-queue [this queue-name options]
    (let [url (make-queues-url this queue-name)
          body (make-body options :queue)
          params (make-params :put url {:body body})]
      (make-request->response this params :queue 200)))
  ;; GET /queues/{queue-name}
  (get-queue [this queue-name]
    (let [url (make-queues-url this queue-name)
          params (make-params :get url {})]
      (make-request->response this params :queue 200)))
  ;; PATCH /queues/{queue-name}
  (update-queue [this queue-name options]
    (let [url (make-queues-url this queue-name)
          body (make-body options :queue)
          params (make-params :patch url {:body body})]
      (make-request->response this params :queue 200)))
  ;; DELETE /queues/{queue-name}
  (delete-queue [this queue-name]
    (let [url (make-queues-url this queue-name)
          params (make-params :delete url {})]
      (make-request->response this params :msg 200)))
  ;; POST /queues/{queue-name}/subscribers
  (set-queue-subscribers [this queue-name subscribers]
    (let [url (make-queues-url this queue-name "subscribers")
          body (make-body subscribers :subscribers)
          params (make-params :post url {:body body})]
      (make-request->response this params :msg 200)))
  ;; PUT /queues/{queue-name}/subscribers
  (replace-queue-subscribers [this queue-name subscribers]
    (let [url (make-queues-url this queue-name "subscribers")
          body (make-body subscribers :subscribers)
          params (make-params :put url {:body body})]
      (make-request->response this params :msg 200)))
  ;; DELETE /queues/{queue-name}/subscribers
  (delete-queue-subscribers [this queue-name subscribers]
    (let [url (make-queues-url this queue-name "subscribers")
          body (make-body subscribers :subscribers)
          params (make-params :delete url {:body body})]
      (make-request->response this params :msg 200)))
  ;; POST /queues/{queue-name}/messages
  (post-messages [this queue-name messages]
    (let [url (make-messages-url this queue-name)
          body (make-body messages :messages)
          params (make-params :post url {:body body})]
      (make-request->response this params :ids 201)))
  (post-message [this queue-name message]
    (post-messages this queue-name [message]))
  ;; POST /queues/{queue-name}/reservations
  (make-reservations [this queue-name] (make-reservations this queue-name {}))
  (make-reservations [this queue-name options]
    (let [url (make-queues-url this queue-name "reservations")
          body (make-body options)
          params (make-params :post url {:body body})]
      (make-request->response this params :messages 200)))
  ;; GET /queues/{queue-name}/messages/{message-id}
  (get-message-by-id [this queue-name message-id]
    (let [url (make-messages-url this queue-name message-id)
          params (make-params :get url {})]
      (make-request->response this params :message 200)))
  ;; GET /queues/{queue-name}/messages
  (peek-messages [this queue-name] (peek-messages this queue-name {}))
  (peek-messages [this queue-name options]
    (let [url (make-messages-url this queue-name)
          params (make-params :get url {:query-params options})]
      (make-request->response this params :messages 200)))
  ;; POST /queues/{queue-name}/messages/{message-id}/touch
  (touch-message [this queue-name message]
    (touch-message this queue-name message {}))
  (touch-message [this queue-name message options]
    (let [{m-id :id r-id :reservation_id} (get-ids message)]
      (touch-message this queue-name m-id r-id options)))
  (touch-message [this queue-name message-id reservation-id options]
    (let [url (make-messages-url this queue-name message-id "touch")
          body (make-body (merge options {:reservation_id reservation-id}))
          params (make-params :post url {:body body})]
      (make-request->response this params :reservation_id 200)))
  ;; POST /queues/{queue-name}/messages/{message-id}/release
  (release-message [this queue-name message]
    (release-message this queue-name message {}))
  (release-message [this queue-name message options]
    (let [{m-id :id r-id :reservation_id} (get-ids message)]
      (release-message this queue-name m-id r-id options)))
  (release-message [this queue-name message-id reservation-id options]
    (let [url (make-messages-url this queue-name message-id "release")
          body (make-body (merge options {:reservation_id reservation-id}))
          params (make-params :post url {:body body})]
      (make-request->response this params :msg 200)))
  ;; DELETE /queues/{queue-name}/messages/{message-id}
  (delete-message [this queue-name message]
    (let [{m-id :id r-id :reservation_id} (get-ids message)]
      (delete-message-by-id this queue-name m-id r-id)))
  (delete-message-by-id [this queue-name message-id]
    (delete-message-by-id this queue-name message-id nil))
  (delete-message-by-id [this queue-name message-id reservation-id]
    (let [url (make-messages-url this queue-name (str message-id))
          body (make-body (str reservation-id) :reservation_id)
          params (make-params :delete url {:body body})]
      (make-request->response this params :msg 200)))
  ;; DELETE /queues/{queue-name}/messages
  (delete-messages [this queue-name messages]
    (let [url (make-messages-url this queue-name)
          body (make-body (mapv get-ids messages) :ids)
          params (make-params :delete url {:body body})]
      (make-request->response this params :msg 200)))
  ;; DELETE /queues/{queue-name}/messages
  (clear-queue [this queue-name]
    (let [url (make-messages-url this queue-name)
          params (make-params :delete url {:body (make-body {})})]
      (make-request->response this params :msg 200)))
  ;; GET /queues/{queue-name}/messages/{message-id}/subscribers
  (get-message-push-statuses [this queue-name message-id]
    (let [url (make-messages-url this queue-name message-id "subscribers")
          params (make-params :get url nil)]
      (make-request->response this params :subscribers 200))))

(defn make-client
  "Makes Client record, which implements IronMQ interface."
  ([] (make-client {}))
  ([user-config & [http-opts]]
   (let [config (merge default-client-config
                       (load-config-from-everywhere)
                       user-config)
         project-id (get-opt config :project_id)
         token (get-opt config :token)
         url (make-base-url config)
         http-options (merge http-opts
                             default-http-options ;; do not rewrite defaults
                             {:headers {:Authorization (str "OAuth " token)}})]
     (Client. project-id token url http-options))))

;;; Helpers functions
(defn post-bodies
  "Function serialises all passed bodies to JSON, makes resulted strings
  messages' bodies, and calls post-messages. Returns the same response as
  post-messages function. It preserves bodies precedence."
  [client queue-name bodies & [options]]
  (let [msgs (mapv #(merge options {:body (json/generate-string %)}) bodies)]
    (post-messages client queue-name msgs)))

(defn post-body [client queue-name body & [options]]
  (post-bodies client queue-name [body] options))
