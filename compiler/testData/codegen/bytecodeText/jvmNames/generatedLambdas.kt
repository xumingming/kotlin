@JvmName("foo")
fun bar(): () -> String = { "bar" }

val val1: () -> String
    @JvmName("getVal2")
    get() = { "val1" }

class A {
    @JvmName("method2")
    fun method1(): () -> String = { "method1" }

    val prop1: () -> String
        @JvmName("getProp2")
        get() = { "prop1" }
}

// 0 NEW GeneratedLambdasKt\$bar\$1
// 1 NEW GeneratedLambdasKt\$foo\$1

// 0 NEW GeneratedLambdasKt\$val1\$1
// 1 NEW GeneratedLambdasKt\$getVal2\$1

// 0 NEW A\$method1\$1
// 1 NEW A\$method2\$1

// 1 GETSTATIC A\$getProp2\$1
// 1 NEW A\$getProp2\$1