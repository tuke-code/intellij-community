// PROBLEM: none
// DISABLE_ERRORS
fun foo(f: () -> Unit) {}

fun test(): Int {
    foo {
        return<caret> 0
    }
    return 1
}
