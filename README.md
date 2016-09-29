
# IronMQ v3 Client Library for Clojure

> Highly available, persistent by design, with one-time delivery, IronMQ
> is the only industrial strength, cloud-native solution for your modern
> application architecture. (c) [iron.io](http://www.iron.io/mq)

This library supports IronMQ v3 HTTP API only. Full API documentation
could be found on [Iron.io's dev site](http://dev.iron.io/mq-onpremise/).

## Artifact

`clj-ironmq` is released to Clojars. Maven users should add the following
repository definition to `pom.xml`:

```xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo/</url>
</repository>
```

### The Most Recent Release

With Leiningen:

[![Clojars Project](http://clojars.org/efficiosro/clj-ironmq/latest-version.svg)](https://clojars.org/efficiosro/clj-ironmq)

With Maven:

```xml
<dependency>
  <groupId>efficiosro</groupId>
  <artifactId>clj-ironmq</artifactId>
  <version>0.1.1</version>
</dependency>
```

## Usage

```clojure
;; In the REPL
(require '[clj-ironmq.core :as imq])

;; In your project
(ns your-project.ns
  (:require [clj-ironmq.core :as imq]))
```

### Create a Client

`clj-ironmq` follows Iron.io's configuration scheme.
See the [configuration documentation](http://dev.iron.io/mq/reference/configuration/).

```clojure
(def client (imq/make-client))
```

It is possible to pass configuration hash to the constructor function:

```clojure
;; In this case, configuration will be loaded from files and environment.
;; But options, supplied by user, will rewrite loaded configuration.
(def client
  (imq/make-client {:project_id "54a6e1d9f7c75b000600000b", :token "TOKEN"}))
```

`clj-ironmq` uses `clj-http` as HTTP client. And it is possible to rewrite
its options, except libraries defaults. See `default-http-options` in the code.

```clojure
;; specify connection timeout (in milliseconds)
(def client (imq/make-client {} {:conn-timeout 5000}))
```

To load configuration from specific file, use `file->config` function:

```clojure
(def client (imq/make-client (imq/file->config "/path/to/configuration/file")))
```

### Get Queues List

[API documentation](http://dev.iron.io/mq-onpremise/reference/api/#list-queues).

```clojure
(def qs-list (imq/get-queues-list client))
;; qs-list => [{:name "queue-name-1"} {:name "queue-name-2"} ...]
(imq/get-queues-list client {:previous (:name (last qs-list))})
;; => [{:name "after-previous-1"} {:name "after-previous-2"} ...]
```

### Create a Queue

[API documentation](http://dev.iron.io/mq-onpremise/reference/api/#create-queue).

```clojure
;; Default IronMQ queue configuration
(imq/create-queue client "first-queue")
;; => {:name "first-queue", :project_id "54a6e1d9f7c75b000600000b",
;;     :message_timeout 60, :message_expiration 604800, :type "pull",
;;     :consistency_factor "full_synchronous", :replicas 2}

;; Provide your own options to queue
(imq/create-queue client "second-queue" {:message_timeout 120})
;; => {:name "second-queue", :project_id "54a6e1d9f7c75b000600000b",
;;     :message_timeout 120, :message_expiration 604800, :type "pull",
;;     :consistency_factor "full_synchronous", :replicas 2}

;; Create push queue
(def pq-info
   {:type "multicast"
    :push {:subscribers [{:name "sub-1", :url "ironmq:///first-queue"}]}})

(imq/create-queue client "push-queue" pq-info)
;; => {:name "push-queue", :project_id "54a6e1d9f7c75b000600000b",
;;     :message_timeout 60, :message_expiration 604800, :type "multicast",
;;     :consistency_factor "full_synchronous", :replicas 2,
;;     :push {:retries 3, :retries_delay 60,
;;            :subscribers [{:name "sub-1", :url "ironmq:///first-queue"}],
;;            :rate_limit -1}}
```

### Get Queue Information

[API documentation](http://dev.iron.io/mq-onpremise/reference/api/#get-queue).

```clojure
(imq/get-queue client "first-queue")
;; => {:consistency_factor "full_synchronous", :name "first-queue",
;;     :type "pull", :total_messages 0, :size 0, :replicas 2,
;;     :project_id "54a6e1d9f7c75b000600000b", :message_timeout 60,
;;     :message_expiration 604800}
```

### Update a Queue

[API documentation](http://dev.iron.io/mq-onpremise/reference/api/#update-queue).

```clojure
(imq/update-queue client "second-queue" {:message_timeout 300})
;; => {:name "second-queue", :project_id "54a6e1d9f7c75b000600000b",
;;     :message_timeout 300, :message_expiration 604800, :type "pull",
;;     :consistency_factor "full_synchronous", :replicas 2}
```

### Delete a Queue

[API documentation](http://dev.iron.io/mq-onpremise/reference/api/#delete-queue).

```clojure
(imq/delete-queue client "first-queue")
;; => "Deleted"
```

### Set Queue Subscribers

This is related to [push queues](http://dev.iron.io/mq-onpremise/reference/push_queues/) only. [API documentation](http://dev.iron.io/mq-onpremise/reference/api/#add-subscribers).

```clojure
(def set-subs
  [{:name "sub-1", :url "http://dev.null.host.co/push"}
   {:name "sub-2", :url "ironmq:///second-queue"}])

(imq/set-queue-subscribers client "push-queue" set-subs)
;; => "Updated"
;; Use `(imq/get-queue client "push-queue")` to see the changes.
```

### Replace Queue Subscribers

This is related to [push queues](http://dev.iron.io/mq-onpremise/reference/push_queues/) only. [API documentation](http://dev.iron.io/mq-onpremise/reference/api/#replace-subscribers).

```clojure
(def new-subs
  [{:name "sub-0", :url "ironmq:///first-queue"}
   {:name "sub-1", :url "ironmq:///second-queue"}])

(imq/replace-queue-subscribers client "push-queue" new-subs)
;; => "Updated"
;; Use `(imq/get-queue client "push-queue")` to see the changes.
```

### Delete Queue Subscribers

This is related to [push queues](http://dev.iron.io/mq-onpremise/reference/push_queues/) only. [API documentation](http://dev.iron.io/mq-onpremise/reference/api/#remove-subscribers).

```clojure
(imq/delete-queue-subscribers client "push-queue" [{:name "sub-1"}])
;; => "Updated"
;; Use `(imq/get-queue client "push-queue")` to see the changes.
```

### Post Messages to a Queue

[API documentation](http://dev.iron.io/mq-onpremise/reference/api/#post-messages).

```clojure
;; Post single message
(imq/post-message client "first-queue" {:body "something"})
;; => ["6112035678239908289"]

;; Post multiple messages
(def msgs
  [{:body "I must be a string!"}
   {:body "{\"one\":1,\"two\":\"2\"}"}])

(imq/post-message client "first-queue" msgs)
;; => ["6112036502873629122" "6112036502873629123"]
```

### Reserve Messages

[API documentation](http://dev.iron.io/mq-onpremise/reference/api/#reserve-messages).

```clojure
;; Make default reservation
(imq/make-reservations client "first-queue")
;; => [{:id "6112035678239908289", :body "something",
;;      :reservation_id "16a172bbeeba0d5b9ef1e4929c5ed131", :reserved_count 1}]

;; With options you are able to reserve more, than one message
(imq/make-reservations client "first-queue" {:n 2})
;; => [{:id "6112036502873629122", :body "something",
;;      :reservation_id "e3ba0609695068f7b7ce5d4f05c0b9f4", :reserved_count 1}
;;     {:id "6112036502873629123", :body "{\"one\":1,\"two\":\"2\"}",
;;      :reservation_id "b441e52c0a5e00a8a8fe9dfacc23ce0e", :reserved_count 1}]
```

### Get Message by ID

[API documentation](http://dev.iron.io/mq-onpremise/reference/api/#get-message-by-id).

```clojure
(imq/get-message-by-id client "first-queue" "6112035678239908289")
;; => {:id "6112035678239908289", :body "something", :reserved_count 1}
```

### Peek Messages

[API documentation](http://dev.iron.io/mq-onpremise/reference/api/#peek-messages).

```clojure
(imq/peek-messages client "first-queue")
;; => [{:id "6112035678239908289", :body "something", :reserved_count 1}]

;; Remember, that peeked messages are not reserved.
(imq/peek-messages client "first-queue" {:n 3})
;; => [{:id "6112035678239908289", :body "something", :reserved_count 1}
;;     {:id "6112036502873629122", :body "something", :reserved_count 1}
;;     {:id "6112036502873629123", :body "{\"one\":1,\"two\":\"2\"}",
;;      :reserved_count 1}]
```

### Touch Reserved Message

[API documentation](http://dev.iron.io/mq-onpremise/reference/api/#touch-message).

```clojure
;; Reserve a message first
(def m (first (imq/make-reservations client "first-queue")))
;; m => {:id "6112035678239908289", :body "something",
;;       :reservation_id "0752b7945cc62082e5587f9b9c8dc499", :reserved_count 2}

;; Touch message, receive new reservation ID
(def rsrv-id (imq/touch-message client "first-queue" m))
;; rsrv-id => "7eca906ced07cf0230b3e00a857e8f1c"
;; or
;; (imq/touch-message client "first-queue" m {:timeout 30})

;; Touch once again
(imq/touch-message client "first-queue" (:id m) rsrv-id {:timeout 20})
;; => "faf637123fdef1f06977a92448c07d76"
```

### Release Reserved Message

[API documentation](http://dev.iron.io/mq-onpremise/reference/api/#release-message).

```clojure
;; Reserve a message first
(def m (first (imq/make-reservations client "first-queue")))
;; m => {:id "6112036502873629123", :body "{\"one\":1,\"two\":\"2\"}",
;;       :reservation_id "a239cd3e6d9fb6b771ef271ab2c640a3", :reserved_count 3}

(imq/release-message client "first-queue" m)
;; => "Released"
;; or
;; (imq/release-message client "first-queue" m {:delay 20})
;; or
;; (imq/release-message client "first-queue" msg-id rsrv-id {:delay 30})
```

### Delete Messages

1.  Delete Single Message

    [API documentation](http://dev.iron.io/mq-onpremise/reference/api/#delete-message).
    
    ```clojure
    ;; Make default reservation
    (def m (first (imq/make-reservations client "first-queue")))
    ;; m => {:id "6112035678239908289", :body "something",
    ;;       :reservation_id "47f039de1e93a6d82943204506fafd25", :reserved_count 4}
    
    (imq/delete-message client "first-queue" m)
    ;; => "Deleted"
    ;; or
    ;; (imq/delete-message-by-id client "first-queue" (:id m) (:reservation_id m))
    
    ;; Create new message
    (def m-id (first (imq/post-messages client "first-queue" [{:body "test-msg"}])))
    ;; m-id => "6112314241228734465"
    
    (imq/delete-message-by-id client "first-queue" m-id)
    ;; => "Deleted"
    ```

2.  Delete Batch of Reserved Messages

    [API documentation](http://dev.iron.io/mq-onpremise/reference/api/#delete-messages).
    
    ```clojure
    (def ms (imq/make-reservations client "first-queue" {:n 2}))
    ;; ms => [{:id "6112036502873629122", :body "something",
    ;;         :reservation_id "614af50cc1cb383d1dd7824153a932a7", :reserved_count 3}
    ;;        {:id "6112036502873629123", :body "{\"one\":1,\"two\":\"2\"}",
    ;;         :reservation_id "0d38f6ee167af0e05ea8d2c4eb571ba9", :reserved_count 3}]
    
    (imq/delete-messages client "first-queue" ms)
    ;; => "Deleted"
    ```

3.  Delete All Messages / Clear a Queue

    [API documentation](http://dev.iron.io/mq-onpremise/reference/api/#clear-messages).
    
    ```clojure
    (imq/clear-queue client "first-queue")
    ;; => "Cleared"
    ```

### Get Message's Push Statuses

This is related to [push queues](http://dev.iron.io/mq-onpremise/reference/push_queues/) only. [API documentation](http://dev.iron.io/mq-onpremise/reference/api/#get-push-statuses).

```clojure
(imq/post-message client "push-queue" {:body "something"})
;; => ["6112072602073752004"]
(imq/get-message-push-statuses client "push-queue" "6112072602073752004")
;; => [{:subscriber_name "sub-0", :retries_remaining 3, :retries_total 3,
;;      :status_code 200, :url "ironmq:///first-queue",
;;      :msg "Message was pushed successfully.",
;;      :last_try_at "2015-02-04T19:23:18.823762185Z"}]
```

### Helper Functions

Only two helper functions, `post-bodies` and `post-body`, are available.
They serialises passed bodies to JSON, make messages with resulted strings
as bodies, merge with provided options, and post to IronMQ.

```clojure
(imq/post-bodies client "first-queue"
                 ["somestring" ["or" "array"] 12 {:and "hash"}])
;; => ["6112036502873629200" "6112036502873629201" "6112036502873629202"
;;     "6112036502873629203"]
;; It sent the next messages:
;; => [{:body "\"somestring\""} {:body "[\"or\",\"array\"]"}
;;     {:body "12"} {:body "{\"and\":\"hash\"}"}]

(imq/post-bodies client "first-queue" ["first" "second"] {:delay 10})
;; => ["6112036504973629334", "6112036504973629335"]
;; It sent the next messages:
;; => [{:body "\"first\"", :delay 10} {:body "\"second\"", :delay 10}]

(imq/post-body client "first-queue" {:my "body"} {:delay 30})
;; => "6112036504973630987"
;; Message, that was sent:
;; {:body "{\"my\":\"body\"}", :delay 30}
```

### Error Handling

`clj-ironmq` tries to do not raise exceptions. In the case of success, it
returns entity of IronMQ's response: queues, messages, etc. Some operations
do not return entity, but operation status. In this case, the library returns
string status.

If IronMQ returns error, or other HTTP issue is accrued, `clj-ironmq` returns
hash with two fields:

```clojure
{:status 503 ;; HTTP status code, if available
 :msg "Internal service error"} ;; message from IronMQ, if provided
```

## Contribution and Tests

I will be pleased to see any propositions to make the library better, if you
know how, please, create an issue or make a pull request.

Currently, there are no tests. I assume, that they are valueless. The most of
the code is implementation of interface, functions are simple and small.
If you think different, feel free to make pull request.

## Copyright and License

Copyright (c) 2015 Yury Yantsevich, [Efficio s.r.o.](http://www.efficio.cz)

The MIT License.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
