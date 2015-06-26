public inline fun fails(block: () -> Unit): Throwable? {
    var thrown: Throwable? = null
    try {
        block()
    } catch (e: Throwable) {
        thrown = e
    }
    if (thrown == null)
        throw Exception("Expected an exception to be thrown")
    return thrown
}

public inline fun throwIt(msg: String) {
    throw Exception(msg)
}

fun box(): String {
    fails {
        throwIt("oops!")
    }
    return "OK"
}
