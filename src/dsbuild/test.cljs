(ns dsbuild.test
  (:require [dsbuild.core :as dsb]
            [dsbuild.styles :as s]
            [serum.tests :refer [slideshow]]
            [dsbuild.leaves]
            [serum.core :refer [scomp mount << $] :refer-macros [afn sfn]]
            [rum.core :as rum]
            [dommy.core :refer-macros [sel1 sel]]
            [datascript.core :as d]))

(dsb/add-entities!
  {:name    :tag
   :members {:name :name}}

  {:name    :image
   :members {:name :name
             :data :data
             :tags [:tag]}
   :comps   {:ref-view (dsb/mviews :data)}}

  {:name    :service
   :members {:name        :name
             :avatar      :image
             :description :text
             :images      [:image]}
   :comps   {:ref-view (dsb/mviews :name :avatar)}}

  {:name    :personel
   :members {:name        :name
             :avatar      :image
             :description :text
             :services    [:service]
             :images      [:image]}
   :comps   {:ref-view (dsb/mviews :name :avatar)}
   :cmods   {:edit (dsb/labelize-members)}}

  {:name    :local
   :members {:name        :name
             :avatar      :image
             :description :text
             :services    [:service]
             :images      [:image]}
   :comps   {:ref-view (dsb/mviews :name :avatar)}
   :cmods   {:view (dsb/labelize-members :services :images)}}

  {:name    :article
   :members {:name    :name
             :avatar  :image
             :content :text
             :images  [:image]
             :authors [:personel]}
   :comps   {:ref-view (dsb/mviews :name :avatar)}})

(dsb/add-comps!
  :tag
  {:index
   (scomp
     {:body (sfn {c :rum/react-component}
                 (cons
                   [:div {:style (s/entity-index-button :limegreen)
                          :on-click (fn [_] (dsb/new-instance :tag) (rum/request-render c))}
                    "new tag"]
                   (for [tid (dsb/ids-for :tag)]
                     (<< (dsb/get-comp :tag :edit)
                         {:args  {:kw :tag :id tid}}))))})}
  :image
  {:many-refs-view
   (scomp
     {:body
      (afn {{pkw :kw pid :id} :parent kw :kw}
           (let [images (mapv #(:data (dsb/id->hm* (:db/id %))) (kw (dsb/id->hm* pid)))]
             [[slideshow {:args {:urls images :sel (atom 0)}}]]))})})

(dsb/conn!)

(dsb/gen-all! 5)

(def ids
  {:tag (rand-nth (dsb/ids-for :tag))
   :personel (rand-nth (dsb/ids-for :personel))
   :image (rand-nth (dsb/ids-for :image))
   :service (rand-nth (dsb/ids-for :service))
   :article (rand-nth (dsb/ids-for :article))
   :local (rand-nth (dsb/ids-for :local)) })

(comment
  (d/transact! dsb/db [[:db.fn/retractEntity 5]])
  ;leaves
  (mount (<< (dsb/get-comp :name :view) {:args {:parent {:id (:tag ids) :kw :tag} :kw :name}}))
  (mount (<< (dsb/get-comp :name :edit) {:args {:parent {:id (:tag ids) :kw :tag} :kw :name}}))

  (mount (<< (dsb/get-comp :text :view) {:args {:parent {:id (:personel ids) :kw :personel} :kw :description}}))
  (mount (<< (dsb/get-comp :text :edit) {:args {:parent {:id (:personel ids) :kw :personel} :kw :description}}))

  (mount (<< (dsb/get-comp :data :view) {:args {:parent {:id (:image ids) :kw :image} :kw :data}}))
  (mount (<< (dsb/get-comp :data :edit) {:args {:parent {:id (:image ids) :kw :image} :kw :data}}))

  ;entities
  (mount (<< (dsb/get-comp :tag :view) {:args {:id (:tag ids) :kw :tag}}))
  (mount (<< (dsb/get-comp :tag :edit) {:args {:id (:tag ids) :kw :tag}}))

  (mount (<< (dsb/get-comp :image :view) {:args {:id (:image ids) :kw :image}}))
  (mount (<< (dsb/get-comp :image :edit) {:args {:id (:image ids) :kw :image}}))

  (mount (<< (dsb/get-comp :service :view) {:args {:id (:service ids) :kw :service}}))
  (mount (<< (dsb/get-comp :service :edit) {:args {:id (:service ids) :kw :service}}))

  (mount (<< (dsb/get-comp :personel :view) {:args {:id (:personel ids) :kw :personel}}))
  (mount (<< (dsb/get-comp :personel :edit) {:args {:id (:personel ids) :kw :personel}}))

  (mount (<< (dsb/get-comp :local :view) {:args {:id (:local ids) :kw :local}}))
  (mount (<< (dsb/get-comp :local :edit) {:args {:id (:local ids) :kw :local}}))

  (mount (<< (dsb/get-comp :article :view) {:args {:id (:article ids) :kw :article}}))
  (mount (<< (dsb/get-comp :article :edit) {:args {:id (:article ids) :kw :article}}))
  )
