// "Let 'B' implement interface 'A<Int>'" "false"
// ACTION: Change parameter 'a' type of function 'foo' to 'B'
// ACTION: Create function 'foo'
// ERROR: Type mismatch: inferred type is B but A<Int> was expected
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'B', but 'A<Int>' was expected.
package let.implement

fun bar() {
    foo(B()<caret>)
}


fun foo(a: A<Int>) {
}

interface A<T>
class B : A<String>