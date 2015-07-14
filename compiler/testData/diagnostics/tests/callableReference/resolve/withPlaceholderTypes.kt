// !DIAGNOSTICS: -UNUSED_PARAMETER,-CONFLICTING_JVM_DECLARATIONS

fun foo(i: Int) = "$i"
fun foo(s: String) = s

fun bar(s: String) = s

fun fn1(x: Int, f1: (Int) -> String, f2: (String) -> String) = f2(f1(x))

fun fn2(f1: (Int) -> String,    f2: (String) -> String  ) = f2(f1(0))
fun fn2(f1: (Int) -> Int,       f2: (Int) -> String     ) = f2(f1(0))
fun fn2(f1: (String) -> String, f2: (String) -> String  ) = f2(f1(""))

val x1 = fn1(1, ::foo, ::foo)
val x2 = fn1(1, ::foo, ::bar)
val x3 = fn2(::bar, ::foo)
val x4 = fn2(::<!OVERLOAD_RESOLUTION_AMBIGUITY!>foo<!>, ::bar)