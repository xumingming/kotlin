class X {
    var result: String = "fail"

    operator fun get(name: String, type: String = "none") = name + type

    operator fun set(name: String, s: String) {
        result = name + s;
    }
}

fun box(): String {
    var x = X()
    x["a"] += "OK"
    if (x.result != "1") return "fail: ${x.result}"

    return "OK"
}