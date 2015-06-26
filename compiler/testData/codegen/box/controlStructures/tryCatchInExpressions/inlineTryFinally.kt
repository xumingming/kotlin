inline fun <T> tryAndThen(f1: () -> Unit, f2: () -> Unit, f3: () -> T): T {
    try {
        f1()
    }
    catch (e: Exception) {
        f2()
    }
    finally {
        return f3()
    }
}

fun foo() {}
fun bar() {}

fun testIt() = "abc" +
               tryAndThen(::foo, ::bar, { "def" }) +
               "ghi"

fun box(): String {
    val test = testIt()
    if (test != "abcdefghi") return "Failed, test==$test"

    return "OK"
}
