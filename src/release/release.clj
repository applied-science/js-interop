(ns release
  (:require [mach.pack.alpha.skinny :as skinny]
            [garamond.main :as garamond]
            [deps-deploy.deps-deploy :as deps-deploy]))

(def GARAMOND-ARGS ["--group-id" "appliedscience"
                    "--artifact-id" "js-interop"])

(def JAR-PATH "target/js_interop.jar")

(defn jar [& args]
  (apply skinny/-main (concat args ["--no-libs" "--project-path" JAR-PATH])))

(defn pom [& args]
  (apply garamond/-main (concat GARAMOND-ARGS ["--pom"] args)))

(defn tag [& [increment-type & args]]
  (let [increment-type (or increment-type "patch")]
    (apply garamond/-main (concat GARAMOND-ARGS args ["--tag" increment-type]))))

(defn -main [command & args]
  (case command
    "jar" (apply jar args)
    "pom" (apply pom args)
    "tag" (apply tag args)
    "deploy" (apply deps-deploy/deploy (concat args [JAR-PATH]))
    nil (-main "release")))
