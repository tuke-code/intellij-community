// LANGUAGE_VERSION: 1.3
// DISABLE_ERRORS

interface Foo

annotation class Ann

class E : @field:Ann @get:Ann @set:Ann <caret>@setparam:Ann Foo

interface G : @Ann Foo