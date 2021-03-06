(disable-warning
 {:linter :constant-test
  :for-macro 'dom-top.core/assert+
  :if-inside-macroexpansion-of #{'clojure.core/let}
  :within-depth nil
  :reason "The codegen performed by dom-top.core/assert+ checks to see if the
           thrown expression is a map at runtime."})

(disable-warning
 {:linter :unused-ret-vals
  :for-macro 'jepsen.util/letr
  :if-inside-macroexpansion-of #{'clojure.test/deftest}
  :within-depth nil
  :reason "We want this intermediate form to go unused! That's what we're
          testing for."})
