(ns clj-crossref.core
  (:require [org.httpkit.client :as hc]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [slingshot.slingshot :refer [throw+]]))

(def api-version "v1.0")

(def api-path (partial str "http://api.crossref.org/" api-version "/"))

(defn- filter-param [filters]
  (->> filters
       (map #(str (-> % first name) ":" (second %)))
       (str/join ","))) 

(defn- facet-param [facets]
    (->> facets
       (map #(str (-> % first name) ":" (second %)))
       (str/join ",")))

(defn- params [spec]
  (let [{:keys [filter facet rows offset sample cursor]} spec]
    (cond-> {}
      filter (assoc :filter (filter-param filter))
      facet  (assoc :facet (facet-param facet))
      rows   (assoc :rows rows)
      offset (assoc :offset offset)
      cursor (assoc :cursor cursor)
      sample (assoc :sample sample))))

(defn- make-request
  ([path]
   (make-request path {}))
  ([path spec]
   (let [{:keys [body status error]}
         (-> path name api-path (hc/get {:query-params (params spec)}) deref)]
     (if (or error (not= 200 status))
       (throw+ {:type ::bad-response
                :status status
                :error error
                :body body})
       (json/read-str body :key-fn keyword)))))

(defn- paged-items
  "Returns a lazy sequence of possibly paged items. Will override
   :rows and :offset in spec."
  ([path]
   (paged-items path {} 0))
  ([path spec]
   (paged-items path spec 0))
  ([path spec offset]
   (let [paged-spec (merge spec {:offset offset :rows 1000})
         items (get-in (make-request path paged-spec) [:message :items])]
     (if (zero? (count items))
       items
       (lazy-cat items (lazy-seq (paged-items path spec (+ offset 1000))))))))

(defn- cursored-items
  "Returns a lazy sequence of possibly paged items. Items are paged using
   cursors. Will override :rows, :offset and :cursor in spec."
  ([path]
   (cursored-items path {} "*"))
  ([path spec]
   (cursored-items path spec "*"))
  ([path spec cursor]
   (let [paged-spec (-> spec
                        (merge {:rows 1000 :cursor cursor})
                        (dissoc :offset))
         response (make-request path paged-spec)
         items (get-in response [:message :items])
         next-cursor (get-in response [:message :next-cursor])]
     (if (or (nil? next-cursor) (empty? items))
       items
       (lazy-cat items (lazy-seq (cursored-items path spec next-cursor)))))))

(defn- sampled-items
  "Returns a sequence of sampled items meeting the spec"
  ([path size]
   (sampled-items path size {}))
  ([path size spec]
   (get-in (make-request path (assoc spec :sample size)) [:message :items])))

(defn- item-count
  ([path]
   (item-count path {}))
  ([path spec]
   (get-in (make-request path spec) [:message :total-results])))

(defn- get-item [path id]
  (get-in (make-request (str (name path) "/" id)) [:message]))

(defn- breakdown
  ([path k]
   (breakdown path k "*" {}))
  ([path k limit]
   (breakdown path k limit {}))
  ([path k limit spec]
   (-> path
       (make-request (assoc spec :facet {(name k) limit}))
       (get-in [:message :facets k :values]))))

;; public interface

(def work (partial get-item :works))
(def funder (partial get-item :funders))
(def member (partial get-item :members))
(def prefix (partial get-item :prefixes))
(def journal (partial get-item :journals))

(def work-count (partial item-count :works))
(def funder-count (partial item-count :funders))
(def member-count (partial item-count :members))
(def prefix-count (partial item-count :prefixes))
(def journal-count (partial item-count :journals))

(def sampled-works (partial sampled-items :works))
(def sampled-funders (partial sampled-items :funders))
(def sampled-members (partial sampled-items :members))
(def sampled-prefixes (partial sampled-items :prefixes))
(def sampled-journals (partial sampled-items :journals))

(def works (partial cursored-items :works))
(def funders (partial paged-items :funders))
(def members (partial paged-items :members))
(def prefixes (partial paged-items :prefixes))
(def journals (partial paged-items :journals))

(def types (partial paged-items :types))

(def works-breakdown (partial breakdown :works))
  
(defn get-metadata [doi & {:keys [format]
                           :or {format "application/vnd.crossref.unixsd+xml"}}]
  (let [{:keys [body status]}
        @(hc/get (api-path (str "works/" doi "/transform"))
                 {:headers {"Accept" format}})]
    body))

(defn get-work-agency [doi]
  (get-in (make-request (str "works/" doi "/agency")) [:message :agency]))

                                  
