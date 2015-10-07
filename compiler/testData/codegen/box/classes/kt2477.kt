package test

interface A {
    public val c: String
        get() = "OK"
}

open class C {
    private val c: String = "FAIL"
}

open class D: C(), A {
    val b = c
}

fun box() : String {
    return D().c
}
