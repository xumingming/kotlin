open class VeryBase

open class Base : VeryBase()

class Derived : Base() {
    fun original(): VeryBase = this
}

class Another : Base()

fun foo(d: Derived, a: Another?): Base? {
    // d has now same type with d.original() --> VeryBase
    if (d.original() != d) return null
    // Despite of this, ad should be of type Base, not VeryBase
    val ad = a ?: d
    return ad
}