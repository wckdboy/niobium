package ai.wckd.niobium.desktop

import java.io.Closeable
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Prevents two desktop instances from writing the same local engine home. */
class AppLease private constructor(private val channel: FileChannel, private val lock: FileLock) : Closeable {
    override fun close() { lock.release(); channel.close() }
    companion object {
        fun acquire(directory: Path = AppPaths.data): AppLease? {
            AppPaths.privateDirectory(directory)
            val channel = FileChannel.open(directory.resolve("app.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
            if (lock == null) { channel.close(); return null }
            return AppLease(channel, lock)
        }
    }
}
