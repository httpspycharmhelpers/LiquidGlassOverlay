package com.example.liquidglass


internal object CompatApi {

    
    const val BAKLAVA = 36

    
    @Suppress("UNCHECKED_CAST")
    internal fun <T> newColorFilter(agsl: String): T? =
        newInstance("android.graphics.RuntimeColorFilter", agsl) as T?

    
    @Suppress("UNCHECKED_CAST")
    internal fun <T> newXfermode(agsl: String): T? =
        newInstance("android.graphics.RuntimeXfermode", agsl) as T?

    private fun newInstance(className: String, agsl: String): Any? {
        if (android.os.Build.VERSION.SDK_INT < BAKLAVA) return null
        return try {
            val cls = Class.forName(className)
            cls.getConstructor(String::class.java).newInstance(agsl)
        } catch (t: Throwable) {
            null
        }
    }

    
    internal fun setFloatUniform(holder: Any?, name: String, value: Float) {
        if (holder == null) return
        try {
            holder.javaClass.getMethod("setFloatUniform", String::class.java, Float::class.java)
                .invoke(holder, name, value)
        } catch (_: Throwable) {}
    }
}