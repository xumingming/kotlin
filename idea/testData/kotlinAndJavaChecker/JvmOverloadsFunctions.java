import test.kotlin.A;

import static test.kotlin.JvmOverloadsFunctionKt.foo;

class JvmOverloadsFunctions {
    public static void main(String[] args) {
        A a = new A() { };

        foo(a.getClass(), a, true, "Some");
        foo(a.getClass(), a, true);
        foo(a.getClass(), a);
    }
}
