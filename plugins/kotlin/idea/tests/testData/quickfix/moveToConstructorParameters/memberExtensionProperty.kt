// "Move to constructor parameters" "false"
// ACTION: Add getter
// ACTION: Make internal
// ACTION: Make private
// ACTION: Make protected
// ACTION: Move to constructor
// ERROR: Extension property must have accessors or be abstract
// K2_AFTER_ERROR: Extension property must have accessors or be abstract.
class A {
    <caret>val Int.n: Int
}