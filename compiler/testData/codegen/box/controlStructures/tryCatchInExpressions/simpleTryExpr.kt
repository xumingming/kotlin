fun box(): String {
    val test1 = "abc" + try { "def" } catch (e: Exception) { "oops!" }
    if (test1 != "abcdef") return "Failed, test1==$test1"

    val test2 = "abc" + try { throw Exception("oops!") } catch (e: Exception) { "def" }
    if (test2 != "abcdef") return "Failed, test2==$test2"

    val test3 = 1 + try { 10 } finally { 100 }
    if (test3 != 11) return "Failed, test3==$test3"

    return "OK"
}