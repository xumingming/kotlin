// PARAM_TYPES: kotlin.String, Comparable<String>, CharSequence, java.io.Serializable, kotlin.Any
// PARAM_DESCRIPTOR: public fun kotlin.String.test(): kotlin.Unit defined in root package
fun String.foo(f: () -> Unit) {
    f()
}

fun String.test() {
    "sss".foo {
        println(<selection>this@test</selection>)
    }
}