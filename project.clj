(defproject clj-ironmq "0.1.1-SNAPSHOT"
  :description "Native Clojure IronMQ client library."
  :url "https://github.com/featalion/clj-ironmq"
  :scm {:name "git", :url "https://github.com/featalion/clj-ironmq"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [cheshire "5.4.0"]
                 [clj-http "1.0.1"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.2 {:dependencies [[org.clojure/clojure "1.2.1"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha4"]]}}
  :aliases {"all" ["with-profile" "dev,1.3:dev,1.4:dev,1.5:dev,1.7:dev"]})
