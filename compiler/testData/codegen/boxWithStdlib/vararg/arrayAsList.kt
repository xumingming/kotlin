fun box(): String {
    val arr = arrayOf("a", "b", "c", "d", "b", "e")
    val list = arr.asList()

    arr[2] = "xx"
    val alist = arr.toList()
    assert(list == alist) { "Expected: $list, got $alist" }

    return "OK"
}