package extensionMemberFunction

fun main(args: Array<String>) {
    MemberClass().testMember(ExtClass())
    MemberClass.testCompanion(ExtClass())
}

class MemberClass {
    fun testMember(extClass: ExtClass) {
        // EXPRESSION: extClass.testPublic()
        // RESULT: 1: I
        //Breakpoint!
        extClass.testPublic()

        with(extClass) {
            // EXPRESSION: testPublic()
            // RESULT: Unresolved reference: testPublic
            //Breakpoint!
            testPublic()
        }

        // EXPRESSION: extClass.testPrivate()
        // RESULT: 1: I
        //Breakpoint!
        extClass.testPrivate()

        with(extClass) {
            // EXPRESSION: testPrivate()
            // RESULT: Unresolved reference: testPrivate
            //Breakpoint!
            testPrivate()
        }

        extClass.testExtMember()
    }

    fun ExtClass.testExtMember() {
        // EXPRESSION: testPublic()
        // RESULT: Unresolved reference: testPublic
        //Breakpoint!
        testPublic()

        // EXPRESSION: this.testPublic()
        // RESULT: A receiver of type extensionMemberFunction.ExtClass is required
        //Breakpoint!
        this.testPublic()

        // EXPRESSION: testPrivate()
        // RESULT: Unresolved reference: testPrivate
        //Breakpoint!
        testPrivate()

        // EXPRESSION: this.testPrivate()
        // RESULT: A receiver of type extensionMemberFunction.ExtClass is required
        //Breakpoint!
        this.testPrivate()
    }

    public fun ExtClass.testPublic() = a
    private fun ExtClass.testPrivate() = a

    companion object {
        public fun ExtClass.testCompPublic() = a
        private fun ExtClass.testCompPrivate() = a

        fun testCompanion(extClass: ExtClass) {
            // EXPRESSION: extClass.testCompPublic()
            // RESULT: 1: I
            //Breakpoint!
            extClass.testCompPublic()

            with(extClass) {
                // EXPRESSION: testCompPublic()
                // RESULT: Unresolved reference: testCompPublic
                //Breakpoint!
                testCompPublic()
            }

            // EXPRESSION: extClass.testCompPrivate()
            // RESULT: 1: I
            //Breakpoint!
            extClass.testCompPrivate()

            with(extClass) {
                // EXPRESSION: testCompPrivate()
                // RESULT: Unresolved reference: testCompPrivate
                //Breakpoint!
                testCompPrivate()
            }

            extClass.testExtCompanion()
        }

        fun ExtClass.testExtCompanion() {
            // EXPRESSION: testCompPublic()
            // RESULT: Unresolved reference: testCompPublic
            //Breakpoint!
            testCompPublic()

            // EXPRESSION: this.testCompPublic()
            // RESULT: A receiver of type extensionMemberFunction.ExtClass is required
            //Breakpoint!
            this.testCompPublic()

            // EXPRESSION: testCompPrivate()
            // RESULT: Unresolved reference: testCompPrivate
            //Breakpoint!
            testCompPrivate()

            // EXPRESSION: this.testCompPrivate()
            // RESULT: A receiver of type extensionMemberFunction.ExtClass is required
            //Breakpoint!
            this.testCompPrivate()
        }
    }
}

class ExtClass {
    val a = 1
}