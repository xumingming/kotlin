// "Create property 'foo' as constructor parameter" "false"
// ACTION: Convert to expression body
// ACTION: Create local variable 'foo'
// ACTION: Create parameter 'foo'
// ACTION: Create property 'foo'
// ERROR: Unresolved reference: foo

class A {
    object B {
        fun test(): Int {
            return <caret>foo
        }
    }
}
