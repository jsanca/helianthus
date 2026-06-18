package helianthus.core.util

import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class InputStreamUtils {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun getInputStream(path: String): InputStream? {
            return getInputStreamFromClassPath(path)
                    ?: getInputStreamFromFileSystem(path)
        }

        @JvmStatic
        fun getInputStreamFromClassPath(path: String): InputStream? {
            return InputStreamUtils::class.java
                    .classLoader
                    .getResourceAsStream(path)
        }

        @JvmStatic
        @Throws(FileNotFoundException::class)
        fun getInputStreamFromFileSystem(path: String): InputStream {
            return FileInputStream(path)
        }
    }
}
