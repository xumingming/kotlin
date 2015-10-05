import A.foo

object A {
    var foo = "NotOk"
    var String.foo: String
        get() = "K"
        set(i) {}
}

fun box(): String {
    val nonExt = ::foo

    nonExt.set("O")
    val ext = String::foo
    ext.set("", "Whatever")
    return nonExt.get() + ext.get("")
}
