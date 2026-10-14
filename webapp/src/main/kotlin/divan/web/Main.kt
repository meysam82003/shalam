package divan.web

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val list = listOf(3, 1, 2).sorted()
        println("hello from kotlin " + list + " " + String.format("%.2f", 1.5f) + " " + "سلام".length)
    }
}
