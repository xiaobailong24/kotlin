package test.generation

public class ResolvedNames(
) {
    public inner class tester {
        public val TestClass: _TestClass = tester._TestClass() // [RESOLUTION_TO_CLASSIFIER] Constructor of inner class _TestClass can be called only with receiver of containing class
        public inner class _TestClass
    }
}