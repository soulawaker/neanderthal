;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns uncomplicate.neanderthal.internal.api
  (:require [uncomplicate.commons.core :refer [Releaseable]]))

(definterface UploNavigator
  (^long colStart [^long n ^long i])
  (^long colEnd [^long n ^long i])
  (^long rowStart [^long n ^long i])
  (^long rowEnd [^long n ^long i])
  (^long defaultEntry [^long i ^long j]))

(definterface StripeNavigator
  (^long start [^long n ^long j])
  (^long end [^long n ^long j]))

(definterface RealOrderNavigator
  (^long sd [^long m ^long n])
  (^long fd [^long m ^long n])
  (^long index [^long ofst ^long ld ^long i ^long j])
  (^double get [a ^long i ^long j])
  (set [a ^long i ^long j ^double val])
  (stripe [a ^long j]))

(defprotocol Blas
  (amax [this x])
  (iamax [this x])
  (iamin [this x])
  (swap [this x y])
  (copy [this x y])
  (dot [this x y])
  (nrm2 [this x])
  (asum [this x])
  (rot [this x y c s])
  (rotg [this abcs])
  (rotm [this x y params])
  (rotmg [this d1d2xy param])
  (scal [this alpha x])
  (axpy [this alpha x y])
  (mv [this alpha a x beta y] [this a x])
  (rk [this alpha x y a])
  (mm [this alpha a b beta c] [this alpha a b right]))

(defprotocol BlasPlus
  (sum [this x])
  (imax [this x])
  (imin [this x])
  (subcopy [this x y kx lx ky])
  (set-all [this alpha x])
  (axpby [this alpha x beta y])
  (trans [this a]))

(defprotocol Lapack
  (trf [this a ipiv])
  (trs [this a b ipiv])
  (sv [this a b ipiv])
  (qrf [this a tau])
  (qrfp [this a tau])
  (rqf [this a tau])
  (lqf [this a tau])
  (qlf [this a tau])
  (ls [this a b])
  (ev [this a w vl vr])
  (svd [this a s superb] [this a s u vt superb]))

(defprotocol ReductionFunction
  (vector-reduce [f init x] [f init x y] [f init x y z] [f init x y z v])
  (vector-reduce-map [f init g x] [f init g x y] [f init g x y z] [f init g x y z v]))

(defprotocol Factory
  (create-vector [this n init])
  (create-ge [this m n ord init])
  (create-tr [this n ord uplo diag init])
  (vector-engine [this])
  (ge-engine [this])
  (tr-engine [this]))

(defprotocol EngineProvider
  (engine [this]))

(defprotocol FactoryProvider
  (factory [this])
  (native-factory [this])
  (index-factory [this]))

(defprotocol DataAccessorProvider
  (data-accessor ^DataAccessor [this]))

(defprotocol MemoryContext
  (compatible? [this other])
  (fits? [this other])
  (fits-navigation? [this other]))

(defprotocol Container
  (raw [this] [this factory])
  (zero [this] [this factory])
  (host [this])
  (native [this]))

(defprotocol DenseContainer
  (view-ge [this])
  (view-tr [this uplo diag]))

;; ============ Realeaseable ===================================================

(extend-type clojure.lang.Sequential
  Releaseable
  (release [this]
    true)
  Container
  (raw [this fact]
    (let [n (count this)]
      (create-vector fact n false))))

(extend-type Object
  MemoryContext
  (compatible? [this o]
    (instance? (class this) o))
  DataAccessorProvider
  (data-accessor [_]
    nil))

(extend-type nil
  MemoryContext
  (compatible? [this o]
    false)
  DataAccessorProvider
  (data-accessor [_]
    nil))

;; ============================================================================

(def ^:const ROW_MAJOR 101)
(def ^:const COLUMN_MAJOR 102)
(def ^:const DEFAULT_ORDER COLUMN_MAJOR)

(def ^:const NO_TRANS 111)
(def ^:const TRANS 112)
(def ^:const CONJ_TRANS 113)
(def ^:const DEFAULT_TRANS NO_TRANS)

(def ^:const UPPER 121)
(def ^:const LOWER 122)
(def ^:const DEFAULT_UPLO LOWER)

(def ^:const DIAG_NON_UNIT 131)
(def ^:const DIAG_UNIT 132)
(def ^:const DEFAULT_DIAG DIAG_NON_UNIT)

(def ^:const LEFT 141)
(def ^:const RIGHT 142)

(defn dec-property
  [^long code]
  (case code
    101 :row
    102 :column
    111 :no-trans
    112 :trans
    113 :conj-trans
    114 :atlas-conj
    121 :upper
    122 :lower
    131 :non-unit
    132 :unit
    141 :left
    142 :right
    :unknown))

(defn enc-property [option]
  (case option
    :row 101
    :column 102
    :no-trans 111
    :trans 112
    :conj-trans 113
    :atlas-conj 114
    :upper 121
    :lower 122
    :non-unit 131
    :unit 132
    :left 141
    :right 142
    nil))

(defn enc-order ^long [order]
  (if (= :row order) 101 102))

(defn enc-uplo ^long [uplo]
  (if (= :upper uplo) 121 122))

(defn enc-diag ^long [diag]
  (if (= :unit diag) 132 131))
