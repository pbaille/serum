# Serum: compose, extend and style rum components

![alt tag](https://github.com/pbaille/serum/blob/master/resources/public/syringe.png)

CAUTION! Alpha stage

## Usage 

add the following to your dependencies: `[serum "0.1.0-SNAPSHOT"]`

```clojure
(ns my-ns (:require [serum.core :as s])
```

## examples

  scomp function is used to define new components, it takes a map

### :body

```clojure
(mount (scomp {:body ["hello scomp!"]}))
```

the body key should contains a seq representing the body of the component

```clojure
(mount (scomp {:body (fn [state] (println state) ["hello scomp!"])}))
```

it can also hold a function that given the component state return a seq representing the body
  
### :args

```clojure
(mount (scomp {:body (fn [{{a :a} :args}] [[:div a]])
               :args {:a "hello!"}}))             
```

this makes more sense when we actually need the state to build the body!
by the way we discover another option key named :args that simply hold arbitrary state that we need for our component
  
###:schema

```clojure
(mount (scomp {:body (fn [{{a :a} :args}] [[:div a]])
               :schema {:a s/Str}
               :args {:a "hello!"}}))
```
you can add a schema to check args

```clojure
(mount (scomp {:body (fn [{{a :a} :args}] [[:div a]])
               :schema {:a s/Str}
               :args {:a 1}}))
               
``` 

should throw an exception
  
```clojure
(def a0 (atom 0))

(mount (scomp {:body (fn [{{a :a} :args}] [[:div @a]])
               :schema {:a (ref s/Int)}
               :args {:a a0}}))

(swap! a0 inc)

```

if some args are refs that you want the component be reactive on you can tell it like this,the ref function is just a convenience that return a schema
  
  
###:attrs

```clojure
(mount (scomp {:body (fn [{{a :a} :args}] [[:div @a]])
               :attrs (sfn {{a :a} :args} {:on-click (fn [_] (swap! a inc))})
               :schema {:a (ref s/Int)}
               :args {:a a0}}))

(mount (scomp {:body (fn [{{a :a} :args}] [[:div @a]])
               :attrs (afn {a :a} {:on-click (fn [_] (swap! a inc))})
               :schema {:a (ref s/Int)}
               :args {:a a0}}))

(comment
  "those expressions are equivalent"
  (with-meta (fn [{{a :a} :args}] "body") {:type :sfn})
  (sfn {{a :a} :args} "body")
  (afn {a :a} "body"))
```

the attrs option is used to provide one or many attribute-constructor(s) or attribute-map(s),
an attribute constructor is a fn that olds {:type :sfn} in metadata and return an attribute-map,
sfn stands for 'state function' in other words a value that depends on the component state
it can be built with sfn or afn macros (note that first argument is a binding form, for sfn it binds on full state and for afn on args)  

```clojure
(mount (scomp {:body (afn {a :a} [[:div @a]])
               :attrs (afn {a :a} {:on-click (fn [_] (swap! a inc))})
               :schema {:a (ref s/Int)}
               :args {:a a0}}))                
``` 

the `afn` macro provide a cleaner way to declare constructors that cares only about args

```clojure
(mount (scomp {:body (afn {a :a} [[:div @a]])
               :attrs [(afn {a :a} {:on-click (fn [_] (swap! a inc))})
                       {:on-mouse-over (fn [e] (println e))}]
               :schema {:a (ref s/Int)}
               :args {:a a0}}))
``` 

the :attrs option can take several attributes-constructors or attributes-map at a time

###:style
  
```clojure
(mount (scomp {:body (afn {t :text} [[:p t]])
               :style (afn {c :color} {:background-color c})
               :args {:color :lightskyblue :text "Hello!"}}))
```

you can specify styles in the same way than attrs

```clojure
(def ss1 {:background-color :tomato
          :padding :5px
          :border-radius :5px
          :border "3px solid lightcyan"})

(def c1
  (scomp {:body (afn {t :text} [[:p t]])
          :style [ss1 (afn {c :color} {:background-color c})]
          :args {:color :lightskyblue :text "Hello!"}}))

(mount c1)

```

like attrs it can take several at a time

###:bpipe

bpipe option can hold a sequence of body transformations
after the body has been evaluated, it is passed in all body transformations

```clojure
(mount (scomp {:body [c1 [c1 {:args {:text "goodbye!"}}]]
               :bpipe (fn [b] (apply concat (repeat 3 b)))}))

(mount [c1 {:bpipe (fn [b] (repeat 3 (first b)))}])

```

###:mixins

```clojure
(def polite-comp
  {:did-mount (fn [_] (println "Hello!"))})

(mount (scomp {:mixins [polite-comp]
               :body ["yop"]}))
```

you can provide mixins, just like in rum
  
### hiccup like vector litterals

```clojure
(mount [c1 {:args {:color :mediumaquamarine}}])
```

you can provide args to your component like this, it will be merged with the existant args

```clojure
(mount [c1 {:attrs {:on-click (fn [_] (println "yo"))}}])
```

you can extend your component like this, c1 is kept intact, we are just adding a click handler here.
   
### injections, selectors  

```clojure
(def c2 (scomp {:wrapper :.foo
                :body (afn {c :content} c)}))

(def c3 (scomp {:body [[c2 {:args {:content "foo"}}]
                       [c2 {:args {:content "bar"}}]]
                :style {:background-color :purple
                        :padding :10px
                        ($ ".foo") {:background-color :lightcoral
                                    :font-size :25px
                                    :color :white
                                    :padding :10px}}}))

(mount c3)
```

you can inject styles or attributes into sub components 
  
### pseudos classes and css macro

```clojure
  (mount [c3 {:style {:border-radius :5px
                      :hover {:background-color :pink}}}])
```

you can specify :hover :active and :focus styles like this

### attribute and styles merging

```clojure
(def c4 (scomp {:body ["click me and watch console"]
                :attrs {:on-click (fn [_] (println "clicked"))}}))

(mount [c4 {:attrs {:on-click (fn [_] (println "clicked overiden"))}}])
```

when doing this the old click event is overiden by the new

```clojure
(mount [c4 {:attrs (m> {:on-click (fn [_] (println "clicked overiden"))})}])
```

with m> it is added

```clojure
(def default-on-click (m? {:on-click (fn [_] (println "default click"))}))

(def c5 (scomp {:body ["click me"]}))

(mount [c5 {:attrs default-on-click}])
```

when wrap with m? an attribute or style is merged only if not present in the target component

```clojure
(mount [c4 {:attrs default-on-click}])

```

  should not change c4 click

```clojure
  (def wrap-click
    (m! {:on-click
         (fn [click-handler]
           (fn [_] (println "wrap") (click-handler) (println "wrap...")))}))

  (mount [c4 {:attrs wrap-click}])
```

with m! you can swap an attribute value
  
## Usage

```clojure
(def atom1 (atom 1))
(def atom2 (atom 10))
(def atom3 (atom {:a 12 :b 13}))

(def c1
  (scomp {:label :c1
          :wrapper :div#aze.ert
          :attrs [{:on-click (fn [_] (println "yop"))}
                  {:on-mouse-over (fn [_] (println "over"))}
                  (m> (afn {b :b} {:on-click (fn [_] (swap! b inc))}))]
          :style [{:background-color :mediumaquamarine
                   :padding (str "10px")
                   :border (str "10px solid grey")
                   :hover {:background-color :mediumslateblue}
                   :active {:background-color :pink}}
                  (afn {a :a b :b}
                       {:margin (str a "px")
                        :padding (str @b "px")})]
          :body (fn [_] ["Hello scomp!"])
          :schema {:a s/Int :b (ref s/Int)}
          :args {:a 50 :b (cursor atom3 [:b])}}))

(mount [c1 {:style (afn {a :a} {:border (str (/ a 4) "px solid lightskyblue")})
            :attrs (m> {:on-click (fn [_] (println "yep"))})
            :args {:a 12}
            :bpipe (fn [b] (conj b [:div "one"]))}])

(def c2
  (scomp {:wrapper :.qsd
          :label :c2
          :body [c1 c1]}))

(def c3
  (scomp {:body [c2 c2]
          :style {($ :.qsd) (m? {:border "10px solid lightgrey"})
                  ($and ($ :.ert) ($ :$c1)) {:hover {:background-color :lightcoral}}
                  ($or ($ :.zup) ($ :$c1)) {:color :white}
                  ($p #(= :c1 (:label %))) {:font-size :30px}
                  ($and ($ :$c2) ($nth 1 ($ :$c2))) {:background-color :lightcyan}
                  ($or ($ :$c1) ($nth 1 ($ :$c2))) {:background-color :lightcyan}}}))

(mount c3)
```


