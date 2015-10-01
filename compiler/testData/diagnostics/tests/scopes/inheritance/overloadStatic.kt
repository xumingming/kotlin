// FILE: A.java
public class A {
    static void foo() {}
}

// FILE: B.java
public class B extends A {
    static void foo() {}
}

// FILE: 1.kt
class E: B() {
    init {
        foo()
    }
}