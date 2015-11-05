class X {
    fun test() : String {
        if (this["O"] != "OK") return "fail 1: ${this["O"]}"

        return "OK"
    }
}
var result = "fail"

private operator fun X.get(name: String) = name + "K"
private operator fun X.set(name: String, v: String) {
    result = name + v
}

fun box(): String {
    return X().test()
}