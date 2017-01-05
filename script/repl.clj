(use 'figwheel-sidecar.repl-api)
(start-figwheel! {:figwheel-options {:css-dirs ["resources/public/css"]
                                     :server-port 3456}
                  :all-builds (figwheel-sidecar.repl/get-project-cljs-builds)})
(cljs-repl)