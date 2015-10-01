// For mutable collections and related types (e.g., MutableList, MutableListIterator)
// 'as?' should be generated as a single 'safeAs...' intrinsic call
// without instanceof or 'is...'.

fun test() {
    val x: Any = arrayListOf("abc", "def")
    x as? MutableList<*>
}

// 0 INSTANCEOF
// 0 INVOKESTATIC kotlin/jvm/internal/TypeIntrinsics\.isMutableList
// 1 INVOKESTATIC kotlin/jvm/internal/TypeIntrinsics\.safeAsMutableList