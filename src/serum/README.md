# Serum: compose, extend and style rum components

CAUTION! Alpha stage

## examples

  scomp function is used to define new components, it takes a map

### :body

  ```
  (mount (scomp {:body ["hello scomp!"]}))
  ```

  the body key should contains a seq representing the body of the component

  ```
  (mount (scomp {:body (fn [state] (println state) ["hello scomp!"])}))
  ```



  it can also hold a function that given the component state return a seq representing the body
  
### :args

  ```
  (mount (scomp {:body (fn [{{a :a} :args}] [[:div a]])
                 :args {:a "hello!"}}))
                 
  ```

  this makes more sense when we actually need the state to build the body!
  by the way we discover another option key named :args that simply hold arbitrary state that we need for our component
  
###:schema

```
  (mount (scomp {:body (fn [{{a :a} :args}] [[:div a]])
                 :schema {:a s/Str}
                 :args {:a "hello!"}}))

```
  you can add a schema to check args

```
  (mount (scomp {:body (fn [{{a :a} :args}] [[:div a]])
                 :schema {:a s/Str}
                 :args {:a 1}}))
                 
```

  should throw an exception
  
```

  (def a0 (atom 0))

  (mount (scomp {:body (fn [{{a :a} :args}] [[:div @a]])
                 :schema {:a (ref s/Int)}
                 :args {:a a0}}))

  (swap! a0 inc)

```

  if some args are refs that you want the component be reactive on you can tell it like this,the ref function is just a convenience that return a schema
  
  
###:afn and :afns


```
  (mount (scomp {:body (fn [{{a :a} :args}] [[:div @a]])
                 :afn  (fn [{{a :a} :args}] {:on-click (fn [_] (swap! a inc))})
                 :schema {:a (ref s/Int)}
                 :args {:a a0}}))

```

  the afn option is used to provide an attribute-constructor,
  that is just a function that takes state and return an attribute map

```

  (mount (scomp {:body   (fargs {a :a} [[:div @a]])
                 :afn    (fargs {a :a} {:on-click (fn [_] (swap! a inc))})
                 :schema {:a (ref s/Int)}
                 :args   {:a a0}}))
```

  the 'fargs' macro provide a cleaner way to declare constructors that cares only about args
  
```
  (mount (scomp {:body   (fargs {a :a} [[:div @a]])
                 :afns    [(fargs {a :a} {:on-click (fn [_] (swap! a inc))})
                           {:on-mouse-over (fn [e] (println e))}]
                 :schema {:a (ref s/Int)}
                 :args   {:a a0}}))

```

  the afns options is used when you need to give several attributes-constructors or attributes-map at a time
  
###:sfn and :sfns
  
```

  (mount (scomp {:body   (fargs {t :text} [[:p t]])
                 :sfn    (fargs {c :color} {:background-color c})
                 :args   {:color :lightskyblue :text "Hello!"}}))
```

  sfn is like afn but for styles

```
  (def ss1 {:background-color :tomato
            :padding :5px
            :border-radius :5px
            :border "3px solid lightcyan"})

  (def c1
    (scomp {:body   (fargs {t :text} [[:p t]])
            :sfns   [ss1 (fargs {c :color} {:background-color c})]
            :args   {:color :lightskyblue :text "Hello!"}}))

  (mount c1)

```

  sfns can take a seq like afns
  
### <args , <afn , <sfn , <afns and <sfns


```
  (mount (<args c1 {:color :mediumaquamarine}))

```

  you can provide args to your component like this, it will be merged with the existant args

```
  (mount (<afn c1 {:on-click (fn [_] (println "yo"))}))
  (mount (<sfn c1 {:color :white}))

```

this will add given constructors or maps to your component, and both have their seq equivalent, repectivly <afns and <sfns

### <bfn and <bfns

```
  (mount (<bfn c1 (fn [b] (repeat 3 (first b)))))

  (mount (scomp {:body [c1 (<args c1 {:text "goodbye!"})]
                 :bfn  (fn [b] (apply concat (repeat 3 b)))}))

```

  bfn is used to transform the body of a component, it can be be given part of the scomp option map or via <bfn and <bfns functions
  
### :mixins  

```
  (def polite-comp
    {:did-mount (fn [_] (println "Hello!"))})

  (mount (scomp {:mixins [polite-comp]
                 :body ["yop"]}))

```

  you can provide mixins like in rum
  
### injections, selectors  

```
  (def c2 (scomp {:wrapper :.foo
                   :body (fargs {c :content} c)}))

  (def c3 (scomp {:body [(<args c2 {:content "foo"})
                         (<args c2 {:content "bar"})]
                  :sfn  {:background-color :purple
                         :padding :10px
                         ($ ".foo") {:background-color :lightcoral
                                     :font-size :25px
                                     :color :white
                                     :padding :10px}}}))

  (mount c3)

```

  you can inject styles or attributes into sub components 
  
### pseudos classes and css macro

```

  (mount (<sfn c3 (fn [{css :css}]
                    (let [{h? :hover} @css]
                      {:border-radius :5px
                       :background-color (if h? :pink :purple)}))))

```

  you could access :hover :focus and :active in the css atom, but it is a bit verbose

```

  (mount (<sfn c3 (css {:border-radius :5px
                        :hover {:background-color :pink}})))

```

  much cleaner like this


### attribute and styles merging

```

  (def c4 (scomp {:body ["click me and watch console"]
                  :afn  {:on-click (fn [_] (println "clicked"))}}))

  (mount (<afn c4 {:on-click (fn [_] (println "clicked overiden"))}))
  

```

  when doing this the old click event is overiden by the new

```

  (mount (<afn c4 (m> {:on-click (fn [_] (println "clicked overiden"))})))

```

  with m> it is added

```

  (def default-on-click (m? {:on-click (fn [_] (println "default click"))}))

  (def c5 (scomp {:body ["click me"]}))

  (mount (<afn c5 default-on-click))

```

  when wrap with m? an attribute or style is merged only if not present in the target component

```

  (mount (<afn c4 default-on-click))

```

  should not change c4 click

```

  (def wrap-click
    (m! {:on-click
         (fn [click-handler]
          (fn [_] (println "wrap") (click-handler) (println "wrap...")))}))

  (mount (<afn c4 wrap-click))

```
  
  with m! you can swap an attribute value


