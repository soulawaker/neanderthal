;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    uncomplicate.neanderthal.core
  "Contains core type-agnostic linear algebraic functions roughly corresponding
  to functionality defined in BLAS 123. Typically, you would want to require this
  namespace regardless of the actual type (real, complex, CPU, GPU, pure Java etc.)
  of the vectors and matrices that you use.

  In cases when you need to repeatedly call a function from this namespace
  that accesses individual entries, and the entries are primitive, it
  is better to use a primitive version of the function from
  uncomplicate.neanderthal.real namespace.
  Additionally, constructor functions for different specialized types
  (native, GPU, pure java) are in respective specialized namespaces.

  You need to take care to only use vectors and matrices
  of the same type in the same function call. These functions do not support
  arguments of mixed types. For example, you can not call the
  dot function with one double vector (dv) and one float vector (fv),
  or for one vector in the CPU memory and one in the GPU memory.

  ## Examples

  (ns test
    (:require [uncomplicate.neanderthal core native]))

  (ns test
    (:require [uncomplicate.neanderthal core native opencl]))
  "
  (:require [uncomplicate.commons
             [core :refer [release let-release]]
             [utils :refer [cond-into]]]
            [uncomplicate.neanderthal.math :refer [f= pow sqrt]]
            [uncomplicate.neanderthal.internal.api :as api])
  (:import [uncomplicate.neanderthal.internal.api Vector Matrix GEMatrix TRMatrix Changeable]))

(defn vect?
  "Returns true if x implements uncomplicate.neanderthal.internal.api.Vector.
  (vect? (dv 1 2 3)) => true
  (vect? [1 2 3]) => false
  "
  [x]
  (instance? Vector x))

(defn matrix?
  "Returns true if x implements uncomplicate.neanderthal.internal.api.Matrix.
  (matrix? (dge 3 2 [1 2 3 4 5 6])) => true
  (matrix? [[1 2] [3 4] [5 6]]) => false
  "
  [x]
  (instance? Matrix x))

(defmulti transfer!
  "Transfers the data from source in one type of memory, to the appropriate
  destination in another type of memory. Typically you would use it when you want to
  move data between the host memory and the OpenCL device memory. If you want to
  simply move data from one container to another in the same memory space,
  you should use copy.  If you call transfer in one memory space, it would simply
  be copied.

  (transfer! (sv 1 2 3) device-vect)
  (transfer! device-vect (sv 3))
  "
  (fn ([source destination] [(class source) (class destination)])))

(defmethod transfer! [nil Object]
  [source destination]
  (throw (ex-info "You cannot transfer data from nil." {:source source :destination (str destination)})))

(defmethod transfer! [Object nil]
  [source destination]
  (throw (ex-info "You cannot transfer data to nil." {:source (str source) :destination destination})))

(defmethod transfer! [Object Object]
  [source destination]
  (throw (ex-info "You cannot transfer data between these types of objects."
                  {:source (type source) :destination (str destination)})))

(defn transfer
  "Transfers the data to the memory space defined by factory (OpenCL, CUDA, etc.).
  If the factory is not provided, moves the data to the main host memory.
  If x is already in the main memory, makes a fresh copy.

  (transfer (sv [1 2 3]) opencl-factory)
  (transfer (sge 2 3 (range 6)) opencl-factory)

  (transfer (sv [1 2 3]))
  "
  ([factory x]
   (let-release [res (api/raw x factory)]
     (transfer! x res)
     res))
  ([x]
   (api/host x)))

(defn native
  "Ensures that the data x is in the native main memory,
  and if not, transfers it there.

  (let [v (sv [1 2 3])]
    (identical? (native v) v)) => true
  "
  [x]
  (api/native x))

(defn vctr
  "TODO"
  ([factory source]
   (cond
     (integer? source) (if (<= 0 (long source))
                         (api/create-vector factory source true)
                         (throw (ex-info "Vector cannot have a negative dimension." {:source (str source)})))
     (number? source) (.setBoxed ^Changeable (vctr factory 1) 0 source)
     :default (transfer factory source)))
  ([factory x & xs]
   (vctr factory (cons x xs))))

(defn vctr?
  "TODO
  "
  [x]
  (instance? Vector x))

(defn ge
  "TODO"
  ([factory m n source options]
   (if (and (<= 0 (long m)) (<= 0 (long n)))
     (let-release [res (api/create-ge factory m n (api/enc-order (:order options)) true)]
       (if source
         (transfer! source res)
         res))
     (throw (ex-info "GE matrix cannot have a negative dimension." {:m m :n n}))))
  ([factory ^long m ^long n arg]
   (if (or (not arg) (map? arg))
     (ge factory m n nil arg)
     (ge factory m n arg nil)))
  ([factory ^long m ^long n]
   (ge factory m n nil nil))
  ([factory ^Matrix a]
   (ge factory (.mrows a) (.ncols a) a nil)))

(defn view-ge
  "TODO"
  ([a]
   (api/view-ge a)))

(defn ge?
  "TODO
  "
  [x]
  (instance? GEMatrix x))

(defn tr?
  "TODO
  "
  [x]
  (instance? TRMatrix x))

(defn tr
  "TODO"
  ([factory ^long n source options]
   (if (<= 0 n)
     (let-release [res (api/create-tr factory n (api/enc-order (:order options))
                                      (api/enc-uplo (:uplo options)) (api/enc-diag (:diag options)) true)]
       (if source
         (transfer! source res)
         res))
     (throw (ex-info "TR matrix cannot have a negative dimension." {:n n}))))
  ([factory ^long n arg]
   (if (or (not arg) (map? arg))
     (tr factory n nil arg)
     (tr factory n arg nil)))
  ([factory source]
   (if (number? source)
     (tr factory source nil nil)
     (tr factory (min (.mrows ^Matrix source) (.ncols ^Matrix source)) source nil))))

(defn view-tr
  "TODO"
  ([a]
   (api/view-tr a api/DEFAULT_UPLO api/DEFAULT_DIAG))
  ([^Matrix a options]
   (let [uplo (api/enc-uplo (:uplo options))
         diag (api/enc-diag (:diag options))]
     (api/view-tr a uplo diag))))

;; ================= Container  ================================================

(defn raw
  "Returns an uninitialized instance of the same type and dimension(s)
  as x, which can be neanderthal container."
  ([x]
   (api/raw x))
  ([factory x]
   (api/raw x factory)))

(defn zero
  "Returns an instance of the same type and dimension(s) as the container x,
  filled with 0."
  ([x]
   (api/zero x))
  ([factory x]
   (api/zero x factory)))

;; ================= Vector ====================================================

(defn dim
  "Returns the dimension of the vector x.

  (dim (dv 1 2 3) => 3)
  "
  ^long [^Vector x]
  (.dim x))

(defn subvector
  "Returns a subvector starting witk k, l entries long,
  which is a part of a neanderthal vector x.

  The resulting subvector has a live connection to the
  vector data. Any change to the subvector data will affect
  the vector data.
  If you wish to disconnect the subvector from the parent
  vector, make a copy of the subvector (copy subx) prior
  to any destructive operation.

  (subvector (dv 1 2 3 4 5 6) 2 3)
  => #<RealBlockVector| double, n:3, stride:1>(3.0 4.0 5.0)<>
  "
  [^Vector x ^long k ^long l]
  (if (<= (+ k l) (.dim x))
    (.subvector x k l)
    (throw (ex-info "Requested subvector is out of bounds." {:k k :l l :k+l (+ k l) :dim (.dim x)}))))

;; ================= Matrix =======================

(defn mrows
  "Returns the number of rows of the matrix m.

  (mrows (dge 3 2 [1 2 3 4 5 6])) => 3
  "
  ^long [^Matrix a]
  (.mrows a))

(defn ncols
  "Returns the number of columns of the matrix m.
  (mrows (dge 3 2 [1 2 3 4 5 6])) => 2
  "
  ^long [^Matrix a]
  (.ncols a))

(defn row
  "Returns the i-th row of the matrix m as a vector.

  The resulting vector has a live connection to the
  matrix data. Any change to the vector data will affect
  the matrix data.
  If you wish to disconnect the vector from the parent
  matrix, make a copy of the vector (copy x) prior
  to any changing operation.

  (row (dge 3 2 [1 2 3 4 5 6]) 1)
  => #<RealBlockVector| double, n:2, stride:3>(2.0 5.0)<>
  "
  [^Matrix a ^long i]
  (if (< -1 i (.mrows a))
    (.row a i)
    (throw (ex-info "Requested row is out of bounds." {:i i :mrows (.mrows a)}))))

(defn col
  "Returns the j-th column of the matrix m as a vector.

  The resulting vector has a live connection to the
  matrix data. Any change to the vector data will affect
  the matrix data.
  If you wish to disconnect the vector from the parent
  matrix, make a copy of the vector (copy x) prior
  to any changing operation.

  (col (dge 3 2 [1 2 3 4 5 6]) 0)
  => #<RealBlockVector| double, n:3, stride:1>(1.0 2.0 3.0)<>
  "
  [^Matrix a ^long j]
  (if (< -1 j (.ncols a))
    (.col a j)
    (throw (ex-info "Requested column is out of bounds." {:j j :ncols (.ncols a)}))))

(defn dia
  "TODO"
  [^Matrix a]
  (.dia a))

(defn cols
  "Returns a lazy sequence of vectors that represent
  columns of the matrix m.
  These vectors have a live connection to the matrix data.
  "
  [^Matrix a]
  (map #(.col a %) (range (.ncols a))))

(defn rows
  "Returns a lazy sequence of vectors that represent
  rows of the matrix m.
  These vectors have a live connection to the matrix data.
  "
  [^Matrix a]
  (map #(.row a %) (range (.mrows a))))

(defn submatrix
  "Returns a submatrix of m starting with row i, column j,
  that has k columns and l rows.

  The resulting submatrix has a live connection to the
  matrix m's data. Any change to the submatrix data will affect
  m's data.
  If you wish to disconnect the submatrix from the parent
  matrix, make a copy of the submatrix (copy subm) prior
  to any changing operation.

  (submatrix (dge 4 3 (range 12)) 1 1 2 1)
  => #<GeneralMatrix| double, COL, mxn: 2x1, ld:4>((5.0 6.0))<>
  "
  ([^Matrix a i j k l]
   (if (and (<= 0 (long i) (+ (long i) (long k)) (.mrows a))
            (<= 0 (long j) (+ (long j) (long l)) (.ncols a)))
     (.submatrix a i j k l)
     (throw (ex-info "Requested submatrix is out of bounds."
                     {:i i :j j :k k :l l :mrows (.mrows a) :ncols (.ncols a)
                      :i+k (+ (long i) (long k)) :j+l (+ (long j) (long l))}))))
  ([^Matrix a k l]
   (submatrix a 0 0 k l)))

(defn trans!
  "TODO"
  [^Matrix a]
  (api/trans (api/engine a) a))

(defn trans
  "Transposes matrix m, i.e returns a matrix that has
  m's columns as rows.
  The resulting matrix has a live connection to m's data.

  (trans (dge 3 2 [1 2 3 4 5 6]))
  => #<GeneralMatrix| double, ROW, mxn: 2x3, ld:3>((1.0 2.0 3.0) (4.0 5.0 6.0))<>
  "
  [^Matrix a]
  (.transpose a))

;; ============ Vector and Matrix access methods ===============================

(defn entry
  "Returns the BOXED i-th entry of vector x, or ij-th entry of matrix m.
  In case  your container holds primitive elements, or the element have
  a specific structure, it is much better to use the equivalent methods from
  function from uncomplicate.neanderthal.real.

  (entry (dv 1 2 3) 1) => 2.0
  "
  ([^Vector x ^long i]
   (try
     (.boxedEntry x i)
     (catch IndexOutOfBoundsException e
       (throw (ex-info "Requested element is out of bounds of the vector." {:i i :dim (.dim x)})))))
  ([^Matrix a ^long i ^long j]
   (if (and (< -1 i (.mrows a)) (< -1 j (.ncols a)))
     (.boxedEntry a i j)
     (throw (ex-info "Requested element is out of bounds of the matrix."
                     {:i i :j j :mrows (.mrows a) :ncols (.ncols a)})))))

(defn entry!
  "Sets the i-th entry of vector x, or ij-th entry of matrix m,
  or all entries if no index is provided, to BOXED value val and returns
  the updated container.
  In case  your container holds primitive elements, or the element have
  a specific structure, it is much better to use the equivalent methods from
  function from uncomplicate.neanderthal.real.

  (entry! (dv 1 2 3) 2 -5)
  => #<RealBlockVector| double, n:3, stride:1>(1.0 2.0 -5.0)<>
  "
  ([^Changeable x val]
   (.setBoxed x val))
  ([^Changeable x ^long i val]
   (try
     (.setBoxed x i val)
     (catch IndexOutOfBoundsException e
       (throw (ex-info "The element you're trying to set is out of bounds of the vector."
                       {:i i :dim (dim x)})))))
  ([^Matrix a ^long i ^long j val]
   (if (and (< -1 i (.mrows a)) (< -1 j (.ncols a)) (.isAllowed ^Changeable a i j))
     (.setBoxed ^Changeable a i j val)
     (throw (ex-info "The element you're trying to set is out of bounds of the matrix."
                     {:i i :j j :mrows (.mrows a) :ncols (.ncols a)})))))

(defn alter!
  "Alters the i-th entry of vector x, or ij-th entry of matrix m, to te result
  of applying a function f to the old value at that position, and returns the
  updated container.

  In case  your container holds primitive elements, the function f
  must accept appropriate primitive unboxed arguments, and it will work faster
  if it also returns unboxed result.

  (alter! (dv 1 2 3) 2 (fn ^double [^double x] (inc x)))
  #<RealBlockVector| double, n:3, stride:1>(1.0 2.0 4.0)<>
  "
  ([^Changeable x ^long i f]
   (try
     (.alter x i f)
     (catch IndexOutOfBoundsException e
       (throw (ex-info "The element you're trying to alter is out of bounds of the vector."
                       {:i i :dim (dim x)})))))
  ([^Matrix a ^long i ^long j f]
   (if (and (< -1 i (.mrows a)) (< -1 j (.ncols a)))
     (.alter ^Changeable a i j f)
     (throw (ex-info "The element you're trying to alter is out of bounds of the matrix."
                     {:i i :j j :mrows (.mrows a) :ncols (.ncols a)})))))

;;================== BLAS 1 =======================

(defn dot
  "BLAS 1: Dot product.
  Computes the dot product of vectors x and y.

  (dot (dv 1 2 3) (dv 1 2 3)) => 14.0
  "
  [^Vector x ^Vector y]
  (if (and (api/compatible? x y) (api/fits? x y))
    (api/dot (api/engine x) x y)
    (throw (ex-info "You cannot compute dot of incompatible or ill-fitting vectors."
                    {:x (str x) :y (str y)}))))

(defn nrm2
  "BLAS 1: Euclidean norm.
  Computes the Euclidan (L2) norm of vector x.

  (nrm2 (dv 1 2 3)) => 3.7416573867739413
  "
  [x]
  (api/nrm2 (api/engine x) x))

(defn asum
  "BLAS 1: Sum absolute values.
  Sums absolute values of entries of vector x.

  (asum (dv -1 2 -3)) => 6.0
  "
  [x]
  (api/asum (api/engine x) x))

(defn iamax
  "BLAS 1: The index of the largest absolute value.
  The index of the first entry that has the maximum value.

  (iamax (dv 1 -3 2)) => 1
  "
  ^long [x]
  (api/iamax (api/engine x) x))

(defn iamin
  "BLAS 1: The index of the smallest absolute value.
  The index of the first entry that has the maximum value.

  (iamin (dv 1 -3 2)) => 0
  "
  ^long [x]
  (api/iamin (api/engine x) x))

(defn amax
  "TODO "
  [x]
  (api/amax (api/engine x) x))

(defn imax
  "BLAS 1+: The index of the largest value.
  The index of the first entry that has the maximum value.

  (imax (dv 1 -3 2)) => 2
  "
  ^long [x]
  (api/imax (api/engine x) x))

(defn imin
  "BLAS 1+: The index of the smallest value.
  The index of the first entry that has the minimum value.

  (imin (dv 1 -3 2)) => 2
  "
  ^long [x]
  (api/imin (api/engine x) x))

(defn swp!
  "BLAS 1: Swap vectors or matrices.
  Swaps the entries of containers x and y. x and y must have
  equal dimensions. Both x and y will be changed.
  Works on both vectors and matrices.

  If the dimensions of x and y are not compatible,
  throws IllegalArgumentException.

  (def x (dv 1 2 3))
  (def y (dv 3 4 5))
  (swp! x y)
  => #<RealBlockVector| double, n:3, stride:1>(3.0 4.0 5.0)<>
  y
  => #<RealBlockVector| double, n:3, stride:1>(1.0 2.0 3.0)<>
  "
  [x y]
  (if (not (identical? x y))
    (if (and (api/compatible? x y) (api/fits? x y))
      (api/swap (api/engine x) x y)
      (throw (ex-info "You cannot swap data of incompatible or ill-fitting structures."
                      {:x (str x) :y (str y)})))
    x))

(defn copy!
  "BLAS 1: Copy a vector or a matrix.
  Copies the entries of x into y and returns x. x and y must have
  equal dimensions. y will be changed. Also works on
  matrices.

  If provided with length, offset-x, and offset-y, copies a subvector
  of x into a subvector of y, if there is enough space in y.

  If x and y are not compatible, throws IllegalArgumentException.

  (def x (dv 1 2 3))
  (def y (dv 3 4 5))
  (copy! x y)
  => #<RealBlockVector| double, n:3, stride:1>(1.0 2.0 3.0)<>
  y
  => #<RealBlockVector| double, n:3, stride:1>(1.0 2.0 3.0)<>
  "
  ([x y]
   (if (not (identical? x y))
     (if (and (api/compatible? x y) (api/fits? x y))
       (api/copy (api/engine x) x y)
       (throw (ex-info "You cannot copy data of incompatible or ill-fitting structures."
                       {:x (str x) :y (str y)})))
     y))
  ([^Vector x ^Vector y offset-x length offset-y]
   (if (not (identical? x y))
     (if (and (api/compatible? x y)
              (<= (+ (long offset-x) (long length)) (.dim x))
              (<= (+ (long offset-y) (long length)) (.dim y)))
       (api/subcopy (api/engine x) x y (long offset-x) (long length) (long offset-y))
       (throw (ex-info "You cannot copy data of incompatible vectors"
                       {:x (str x) :y (str y) :length length})))
     y)))

(defn copy
  "Returns a new copy the (part of) entries from container x.
  Changes to the resulting vectors do not affect x.

  (copy (dv 1 2 3))
  => #<RealBlockVector| double, n:3, stride:1>(1.0 2.0 3.0)<>
  "
  ([x]
   (let-release [res (api/raw x)]
     (copy! x res)))
  ([x offset length]
   (let-release [res (vctr (api/factory x) length)]
     (copy! x res offset length 0))))

(defn scal!
  "BLAS 1: Scale vector.

  Computes x = ax
  where:
  - x is a vector,
  - a is a scalar.

  Multiplies vector x by scalar alpha, i.e scales the vector.
  After scal!, x will be changed.

  (scal! 1.5  (dv 1 2 3))
  => #<RealBlockVector| double, n:3, stride:1>(1.5 3.0 4.5)<>
  "
  [alpha x]
  (api/scal (api/engine x) alpha x)
  x)

(defn scal
  "TODO"
  [alpha x]
  (let-release [res (copy x)]
    (scal! alpha res)))

(defn rot!
  "BLAS 1: Apply plane rotation.
  "
  ([^Vector x ^Vector y ^double c ^double s]
   (if (and (api/compatible? x y))
     (if (and (<= -1.0 c 1.0) (<= -1.0 s 1.0) (f= 1.0 (+ (pow c 2) (pow s 2))))
       (api/rot (api/engine x) x y c s)
       (throw (ex-info "You cannot rotate vectors with c and s that are not sin and cos."
                       {:c c :s s :errors
                        (cond-into []
                                   (not (<= -1.0 c 1.0)) "c is not between -1.0 and 1.0"
                                   (not (<= -1.0 s 1.0)) "s is not between -1.0 and 1.0"
                                   (not (f= 1.0 (+ (pow c 2) (pow s 2)))) "s^2 + c^2 is not 1.0")})))
     (throw (ex-info "You cannot rotate incompatible vectors."
                     {:x (str x) :y (str y)}))))
  ([x y ^double c]
   (rot! x y c (sqrt (- 1.0 (pow c 2))))))

(defn rotg!;;TODO docs
  "BLAS 1: Generate plane rotation.

  # Description:

  Computes  the  elements  of  a Givens plane rotation matrix:
  .           __    __     __ __    __ __
  .           | c  s |     | a |    | r |
  .           |-s  c | *   | b | =  | 0 |
  .           --    --     -- --    -- --

  where  r = +- sqrt ( a**2 + b**2 )
  and    c**2 + s**2 = 1.

  a, b, c, s are the four entries in the vector x.

  This rotation can be used to selectively introduce zero elements
  into a matrix.

  # Arguments:

  x: contains a, b, c, and s arguments (see the description).
  .  Has to have exactly these four entries, and must have stride = 1.

  # Result:

  x with the elements changed to be r, z, c, s.
  For a detailed description see
  http://www.mathkeisan.com/usersguide/man/drotg.html
  "
  [^Vector abcs]
  (if (< 3 (.dim abcs))
    (api/rotg (api/engine abcs) abcs)
    (throw (ex-info "Vector abcs must have at least 4 elements." {:dim (.dim abcs)}))))

(defn rotm!;;TODO docs
  "BLAS 1: Apply modified plane rotation.
  "
  [^Vector x ^Vector y ^Vector param]
  (if (and (api/compatible? x y) (api/compatible? x param) (api/fits? x y) (< 4 (.dim param)))
    (api/rotm (api/engine x) x y param)
    (throw (ex-info "You cannot apply modified plane rotation with incompatible or ill-fitting vectors."
                    {:x (str x) :y (str y) :param (str param) :errors
                     (cond-into []
                                (not (api/compatible? x y)) "incompatible x and y"
                                (not (api/compatible? x param)) "incompatible x and param"
                                (not (api/fits? x y)) "ill-fitting x and y"
                                (not (< 4 (.dim param))) "param is shorter than 4")}))))

(defn rotmg!
  "BLAS 1: Generate modified plane rotation.

  - d1d2xy must be a vector of at least 4 entries
  - param must be a vector of at least 5 entries
  "
  [^Vector d1d2xy ^Vector param]
  (if (and (api/compatible? d1d2xy param) (< 3 (.dim d1d2xy)) (< 4 (.dim param)))
    (api/rotmg (api/engine param) d1d2xy param)
    (throw (ex-info "You cannot generate modified plane rotation with incompatible or ill-fitting vectors."
                    {:d1d2xy (str d1d2xy) :param (str param) :errors
                     (cond-into []
                                (not (api/compatible? d1d2xy param)) "incompatible d2d2xy and param"
                                (not (< 3 (.dim param))) "d1d2xy is shorter than 3"
                                (not (< 4 (.dim param))) "param is shorter than 4")}))))

(defn axpy!
  "BLAS 1: Vector scale and add. Also works on matrices.

  Computes y = ax + y.
  where:
  x and y are vectors, or matrices
  a is a scalar.

  Multiplies vector x by scalar alpha and adds it to
  vector y. After axpy!, y will be changed.

  If called with 2 arguments, x and y, adds vector x
  to vector y.

  If called with more than 3 arguments, at least every
  other have to be a vector. A scalar multiplier may be
  included before each vector.

  If the dimensions of x and y are not compatible,
  throws IllegalArgumentException.

  (def x (dv 1 2 3))
  (def y (dv 2 3 4))
  (axpy! 1.5 x y)
  => #<RealBlockVector| double, n:3, stride:1>(3.5 6.0 8.5)<>

  y => #<RealBlockVector| double, n:3, stride:1>(3.5 6.0 8.5)<>

  (axpy! x y)
  => #<RealBlockVector| double, n:3, stride:1>(4.5 8.0 11.5)<>

  (axpy! x y (dv 3 4 5) 2 (dv 1 2 3))
  => #<RealBlockVector| double, n:3, stride:1>(10.5 18.0 25.5)<>
  "
  ([alpha x y]
   (if (and (api/compatible? x y) (api/fits? x y))
     (api/axpy (api/engine x) alpha x y)
     (throw (ex-info "You cannot add incompatible or ill-fitting structures."
                     {:x (str x) :y (str y)}))))
  ([x y]
   (axpy! 1.0 x y))
  ([alpha x y & zs]
   (if (number? alpha)
     (do (axpy! alpha x y)
         (loop [z (first zs) zs (rest zs)]
           (if z
             (if (number? z)
               (do (axpy! z (first zs) y) (recur (second zs) (rest (rest zs))))
               (do (axpy! 1.0 z y) (recur (first zs) (rest zs))))
             y)))
     (apply axpy! 1.0 alpha x y zs))))

(declare axpby!)

(defn axpy
  "A pure variant of axpy! that does not change any of the arguments.
  The result is a new instance.
  "
  ([x y]
   (let-release [res (copy y)]
     (axpy! 1.0 x (copy y))))
  ([x y z]
   (if (number? x)
     (let-release [res (copy z)]
       (axpy! x y res))
     (let-release [res (copy y)]
       (axpy! 1.0 x res z))))
  ([x y z w & ws]
   (if (number? x)
     (if (number? z)
       (let-release [res (copy w)]
         (apply axpby! x y z res ws))
       (let-release [res (copy z)]
         (apply axpy! x y res w ws)))
     (apply axpy 1.0 x y z w ws))))

(defn ax
  "Multiplies container x by a scalar a.
  Similar to scal!, but does not change x. The result
  is a new vector instance."
  [alpha x]
  (let-release [res (zero x)]
    (axpy! alpha x res)))

(defn xpy
  "Sums containers x, y & zs. The result is a new vector instance."
  ([x y]
   (let-release [res (copy y)]
     (axpy! 1.0 x res)))
  ([x y & zs]
   (let-release [cy (copy y)]
     (loop [res (axpy! 1.0 x cy) s zs]
       (if s
         (recur (axpy! 1.0 (first s) res) (next s))
         res)))))

(defn axpby!
  "TODO"
  ([alpha x beta y]
   (if (and (api/compatible? x y) (api/fits? x y))
     (api/axpby (api/engine x) alpha x beta y)
     (throw (ex-info "You cannot add incompatible or ill-fitting structures."
                     {:x (str x) :y (str y)}))))
  ([x beta y]
   (axpby! 1.0 x beta y))
  ([x y]
   (axpy! 1.0 x y))
  ([alpha x beta y & zs]
   (if (number? alpha)
     (do (axpby! alpha x beta y)
         (loop [z (first zs) zs (rest zs)]
           (if z
             (if (number? z)
               (do (axpy! z (first zs) y) (recur (second zs) (rest (rest zs))))
               (do (axpy! 1.0 z y) (recur (first zs) (rest zs))))
             y)))
     (apply axpby! 1.0 alpha x beta y zs))))

;;============================== BLAS 2 ========================================

(defn mv!
  "BLAS 2: Matrix-vector multiplication.

  Computes y = alpha a * x + beta * y
  where:
  a is a matrix,
  x and y are vectors,
  alpha and beta are scalars.

  Multiplies the matrix a by the scalar alpha and then multiplies
  the resulting matrix by the vector x. Adds the resulting
  vector to the vector y, previously scaled by the scalar beta.
  Returns the vector y, which contains the result and is changed by
  the operation.

  If alpha and beta are not provided, uses identity value as their values.

  If the dimensions of a, x and y are not compatible,
  throws IllegalArgumentException.

  (def a (dge 3 2 (range 6)))
  (def x (dv 1 2))
  (def y (dv 2 3 4))

  (mv! 2.0 a x 1.5 y)
  => #<RealBlockVector| double, n:3, stride:1>(15.0 22.5 30.0)<>
  "
  ([alpha ^Matrix a ^Vector x beta ^Vector y]
   (if (and (api/compatible? a x) (api/compatible? a y)
            (= (.ncols a) (.dim x)) (= (.mrows a) (.dim y)))
     (api/mv (api/engine a) alpha a x beta y)
     (throw (ex-info "You cannot multiply incompatible or ill-fitting structures."
                     {:a (str a) :x (str x) :y (str y) :errors
                      (cond-into []
                                 (not (api/compatible? a x)) "incompatible a and x"
                                 (not (api/compatible? a y)) "incompatible a and y"
                                 (not (= (.ncols a) (.dim x))) "(dim x) is not equals to (ncols a)"
                                 (not (= (.mrows a) (.dim y))) "(dim y) is not equals to (mrows a)")}))))
  ([alpha a x y]
   (mv! alpha a x 1.0 y))
  ([a x y]
   (mv! 1.0 a x 0.0 y))
  ([^Matrix a ^Vector x];;TODO docs
   (if (and (api/compatible? a x) (= (.ncols a) (.dim x)))
     (api/mv (api/engine a) a x)
     (throw (ex-info "You cannot multiply incompatible or ill-fitting structures."
                     {:a (str a) :x (str x) :errors
                      (cond-into []
                                 (not (api/compatible? a x)) "incompatible a and x"
                                 (not (= (.ncols a) (.dim x))) "(dim x) is not equals to (ncols a)")})))))

(defn mv
  "A pure version of mv! that returns the result
  in a new vector instance. Computes alpha * a * x."
  ([alpha a x beta y]
   (let-release [res (copy y)]
     (mv! alpha a x beta res)))
  ([alpha a x y]
   (mv 1.0 a x 1.0 y))
  ([alpha a x]
   (let-release [res (api/raw (col a 0))]
     (mv! alpha a x 0.0 res)))
  ([a x]
   (if (instance? GEMatrix a)
     (mv 1.0 a x)
     (let-release [res (copy x)]
       (mv! a x)))))

(defn rk!
  "BLAS 2: General rank-1 update.

  Computes a = alpha * x * y' + a
  where:

  alpha is a scalar
  x and y are vectors, y' is a transposed vector,
  a is a mxn matrix.

  If called with 3 arguments, a, x, y, alpha is 1.0.

  (def a (dge 3 2 [1 1 1 1 1 1]))
  (rk! 1.5 (dv 1 2 3) (dv 4 5) a)
  => #<GeneralMatrix| double, COL, mxn: 3x2, ld:3>((7.0 13.0 19.0) (8.5 16.0 23.5))<>
  "
  ([alpha ^Vector x ^Vector y ^Matrix a]
   (if (and (api/compatible? a x) (api/compatible? a y) (= (.mrows a) (.dim x)) (= (.ncols a) (.dim y)))
     (api/rk (api/engine a) alpha x y a)
     (throw (ex-info "You cannot multiply incompatible or ill-fitting structures."
                     {:a (str a) :x (str x) :y (str y) :errors
                      (cond-into []
                                 (not (api/compatible? a x)) "incompatible a and x"
                                 (not (api/compatible? a y)) "incompatible a and y"
                                 (not (= (.ncols a) (.dim x))) "(dim x) is not equals to (ncols a)"
                                 (not (= (.mrows a) (.dim y))) "(dim y) is not equals to (mrows a)")}))))
  ([x y a]
   (rk! 1.0 x y a)))

(defn rk
  "A pure version of rank! that returns the result
  in a new matrix instance.
  "
  ([alpha x y a]
   (let-release [res (copy a)]
     (rk! alpha x y res)))
  ([alpha ^Vector x ^Vector y]
   (let-release [res (ge (api/factory x) (.dim x) (.dim y))]
     (rk! alpha x y res)))
  ([x y]
   (rk 1.0 x y)))

;; =========================== BLAS 3 ==========================================

(defn mm!
  "BLAS 3: Matrix-matrix multiplication.

  Computes c = alpha a * b + beta * c
  where:
  a, b and c are matrices,
  alpha and beta are scalars.

  Multiplies matrix a by scalar alpha and then multiplies
  the resulting matrix by matrix b. Adds the resulting
  matrix to matrix c previously scaled by scalar beta.
  Returns matrix c, which contains the result and is changed by
  the operation.

  Can be called without scalars, with three matrix arguments.

  If the dimensions of a, b and c are not compatible,
  throws IllegalArgumentException.

  (def a (dge 2 3 (range 6)))
  (def b (dge 3 2 (range 2 8)))
  (def c (dge 2 2 [1 1 1 1]))

  (mm! 1.5 a b 2.5 c)
  => #<GeneralMatrix| double, COL, mxn: 2x2, ld:2>((35.5 49.0) (62.5 89.5))<>

  (def c (dge 2 2))
  (mm! a b c)
  => #<GeneralMatrix| double, COL, mxn: 2x2, ld:2>((22.0 31.0) (40.0 58.0))<>
  "
  ([alpha ^Matrix a ^Matrix b beta ^Matrix c];;TODO docs
   (if (and (api/compatible? a b) (api/compatible? a c)
            (= (.ncols a) (.mrows b)) (= (.mrows a) (.mrows c)) (= (.ncols b) (.ncols c)))
     (api/mm (api/engine a) alpha a b beta c)
     (throw (ex-info "You cannot multiply incompatible or ill-fitting matrices."
                     {:a (str a) :b (str b) :c (str c) :errors
                      (cond-into []
                                 (not (api/compatible? a b)) "incompatible a and b"
                                 (not (api/compatible? a c)) "incompatible a and c"
                                 (not (= (.ncols a) (.mrows b))) "(ncols a) is not equals to (mrows b)"
                                 (not (= (.mrows a) (.mrows c))) "(mrows a) is not equals to (mrows c)"
                                 (not (= (.ncols b) (.ncols c))) "(ncols b) is not equals to (ncols c)")}))))
  ([alpha a b c]
   (mm! alpha a b 1.0 c))
  ([alpha ^Matrix a ^Matrix b]
   (if (and (api/compatible? a b) (= (.ncols a) (.mrows b)))
     (api/mm (api/engine a) alpha a b true)
     (throw (ex-info "You cannot multiply incompatible or ill-fitting matrices."
                     {:a (str a) :b (str b) :errors
                      (cond-into []
                                 (not (api/compatible? a b)) "incompatible a and b"
                                 (not (= (.ncols a) (.mrows b))) "(ncols a) is not equals to (mrows b)")}))))
  ([a b]
   (mm! 1.0 a b)))

(defn mm
  "A pure version of mm!, that returns the result
  in a new matrix instance.
  Computes alpha a * b"
  ([alpha ^Matrix a ^Matrix b beta ^Matrix c]
   (let-release [res (copy c)]
     (mm! alpha a b beta res)))
  ([alpha ^Matrix a ^Matrix b ^Matrix c]
   (mm alpha a b 1.0 c))
  ([alpha ^Matrix a ^Matrix b]
   (if (instance? GEMatrix b)
     (if (instance? GEMatrix a)
       (let-release [res (ge (api/factory b) (.mrows a) (.ncols b))]
         (mm! alpha a b 1.0 res))
       (let-release [res (copy b)]
         (mm! alpha a res)))
     (let-release [res (copy a)]
       (mm! alpha res b))))
  ([a b]
   (mm 1.0 a b)))

;; ============================== BLAS Plus ====================================

(defn sum
  "Sums values of entries of x.

  (sum (dv -1 2 -3)) => -2.0
  "
  [x]
  (api/sum (api/engine x) x))
