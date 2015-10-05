import A.foo

object A {
    fun foo() = "O"
    fun String.foo() = "K"
}

fun box(): String {
    return (::foo)() + (String::foo)("")
}
