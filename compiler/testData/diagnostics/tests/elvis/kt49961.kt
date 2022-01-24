class Xyz {
    fun x(): String? {
        return try {
            [a] ?: XYZ
        }
        catch (e: Exception) {
            null
        }
    }
}