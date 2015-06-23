(ns ring.mock.request-test
  (:require [clojure.java.io :as io]
            [clojure.string :as s])
  (:use clojure.test
        ring.mock.request))

(deftest test-request
  (testing "relative uri"
    (is (= (request :get "/foo")
           {:server-port 80
            :server-name "localhost"
            :remote-addr "localhost"
            :uri "/foo"
            :query-string nil
            :scheme :http
            :request-method :get
            :headers {"host" "localhost"}})))
  (testing "absolute uri"
    (let [request (request :post "https://example.com:8443/foo?bar=baz" {"quux" "zot"})
          literal-request (dissoc request :body)
          body (:body request)]
      (is (= literal-request
             {:server-port 8443
              :server-name "example.com"
              :remote-addr "localhost"
              :uri "/foo"
              :query-string "bar=baz"
              :scheme :https
              :request-method :post
              :content-type "application/x-www-form-urlencoded"
              :content-length 8
              :headers {"host" "example.com:8443"
                        "content-type" "application/x-www-form-urlencoded"
                        "content-length" "8"}}))
      (is (= (slurp body) "quux=zot"))))
  (testing "nil path"
    (is (= (:uri (request :get "http://example.com")) "/")))
  (testing "only params in :get"
    (is (= (:query-string (request :get "/?a=b"))
           "a=b")))
  (testing "added params in :get"
    (is (= (:query-string (request :get "/" (array-map :x "y" :z "n")))
           "x=y&z=n"))
    (is (= (:query-string (request :get "/?a=b" {:x "y"}))
           "a=b&x=y"))
    (is (= (:query-string (request :get "/?" {:x "y"}))
           "x=y"))
    (is (= (:query-string (request :get "/" {:x "a b"}))
           "x=a+b")))
  (testing "added params in :post"
    (let [req (request :post "/" (array-map :x "y" :z "n"))]
      (is (= (slurp (:body req))
             "x=y&z=n"))
      (is (nil? (:query-string req))))
    (let [req (request :post "/?a=b" {:x "y"})]
      (is (= (slurp (:body req))
             "x=y"))
      (is (= (:query-string req)
             "a=b")))
    (let [req (request :post "/?" {:x "y"})]
      (is (= (slurp (:body req))
             "x=y"))
      (is (= (:query-string req)
             "")))
    (let [req (request :post "/" {:x "a b"})]
      (is (= (slurp (:body req))
             "x=a+b"))
      (is (nil? (:query-string req))))
    (let [req (request :post "/?a=b")]
      (is (nil? (:body req)))
      (is (= (:query-string req)
             "a=b"))))
  (testing "added params in :put"
    (let [req (request :put "/" (array-map :x "y" :z "n"))]
      (is (= (slurp (:body req)) "x=y&z=n")))))

(deftest test-header
  (is (= (header {} "X-Foo" "Bar")
         {:headers {"x-foo" "Bar"}}))
  (is (= (header {} :x-foo "Bar")
         {:headers {"x-foo" "Bar"}})))

(deftest test-content-type
  (is (= (content-type {} "text/html")
         {:content-type "text/html"
          :headers {"content-type" "text/html"}})))

(deftest test-content-length
  (is (= (content-length {} 10)
         {:content-length 10
          :headers {"content-length" "10"}})))

(deftest test-query-string
  (testing "string"
    (is (= (query-string {} "a=b")
           {:query-string "a=b"})))
  (testing "map of params"
    (is (= (query-string {} {:a "b"})
           {:query-string "a=b"})))
  (testing "overwriting"
    (is (= (-> {}
               (query-string {:a "b"})
               (query-string {:c "d"}))
           {:query-string "c=d"}))))

(deftest test-body
  (testing "string body"
    (let [resp (body {} "Hello World")]
      (is (instance? java.io.InputStream (:body resp)))
      (is (= (slurp (:body resp)) "Hello World"))
      (is (= (:content-length resp) 11))))
  (testing "map body"
    (let [resp (body {} (array-map :foo "bar" :fi ["fi" "fo" "fum"]))]
      (is (instance? java.io.InputStream (:body resp)))
      (is (= (slurp (:body resp)) "foo=bar&fi=fi&fi=fo&fi=fum"))
      (is (= (:content-length resp) 26))
      (is (= (:content-type resp)
             "application/x-www-form-urlencoded"))))
  (testing "bytes body"
    (let [resp (body {} (.getBytes "foo"))]
      (is (instance? java.io.InputStream (:body resp)))
      (is (= (slurp (:body resp)) "foo"))
      (is (= (:content-length resp) 3))))
  (testing "file upload body"
    (let [resp (body {} (->FileUpload "f" "file contents"))]
      (is (instance? java.io.InputStream (:body resp)))
      (is (= (-> resp :content-type (s/split #";") first) "multipart/form-data"))
      (is (> (:content-length resp) 13))
      (is (re-find #"file contents" (slurp (:body resp)))))))
