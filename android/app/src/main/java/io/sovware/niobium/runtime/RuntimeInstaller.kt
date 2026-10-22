package io.sovware.niobium.runtime

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import java.util.UUID

data class PreparedRuntime(val root: File, val home: File, val temporary: File, val libraries: File,
                           val native: File, val version: String)

class RuntimeInstaller(private val context: Context) {
    fun prepare(cancelled: () -> Boolean = { false }, progress: (Int) -> Unit = {}): PreparedRuntime {
        val manifest = context.assets.open("niobium-runtime.json").bufferedReader().use { JSONObject(it.readText()) }
        val image = manifest.getJSONObject("rootfs")
        val expected = image.getString("sha256")
        val base = File(context.noBackupFilesDir, "local-dsh").apply { mkdirs() }
        val native = File(context.applicationInfo.nativeLibraryDir)
        val libraries = File(base, "libraries").apply { mkdirs() }
        val temporary = File(base, "temporary").apply { mkdirs() }
        base.listFiles()?.filter { it.name.startsWith("rootfs.pending-") }?.forEach(::removeTree)
        val hashes = manifest.getJSONObject("native_sha256")
        for ((source, target) in listOf("libniobium_talloc.so" to "libtalloc.so.2",
                                       "libniobium_android_shmem.so" to "libandroid-shmem.so")) {
            val from = File(native, source)
            check(from.isFile && sha(from.inputStream(), cancelled) == hashes.getString(source)) { "Native runtime checksum failed." }
            val pending = File(libraries, target + ".pending")
            from.copyTo(pending, overwrite = true)
            Files.move(pending.toPath(), File(libraries, target).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        val root = File(base, "rootfs")
        val home = File(base, "home")
        val previous = File(base, "rootfs.previous")
        if (!root.exists() && previous.exists()) check(previous.renameTo(root))
        val marker = File(root, ".niobium-image")
        if (!marker.isFile || marker.readText() != expected) {
            check(base.usableSpace > image.getLong("installed_bytes_4k") + 128L * 1024 * 1024) { "Free at least 550 MB to install local DSH." }
            progress(2)
            context.assets.open("niobium-runtime.image").use {
                check(sha(it, cancelled) == expected) { "The Linux image checksum failed." }
            }
            val stage = File(base, "rootfs.pending-" + UUID.randomUUID()).apply { mkdirs() }
            try {
                RuntimeArchive().extract(context.assets.open("niobium-runtime.image"), stage, cancelled) { bytes ->
                    progress(10 + (bytes * 65 / image.getLong("unpacked_bytes")).toInt().coerceAtMost(65))
                }
                check(File(stage, "usr/bin/node").isFile && File(stage, "usr/local/bin/dsh-serve").isFile) { "The Linux image is incomplete." }
                if (cancelled()) throw InterruptedException("Runtime preparation cancelled.")
                // Home lives outside the replaceable image. Never remove a user's workspace on an upgrade.
                if (!home.exists()) check(File(stage, "root").renameTo(home)) { "Could not prepare the local workspace." }
                File(stage, "root").mkdirs()
                File(stage, ".niobium-image").writeText(expected)
                removeTree(previous)
                if (root.exists()) check(root.renameTo(previous)) { "Could not stage the Linux upgrade." }
                if (!stage.renameTo(root)) {
                    previous.renameTo(root)
                    error("Could not install the Linux image.")
                }
                removeTree(previous)
            } finally { removeTree(stage) }
        }
        check(home.isDirectory) { "The local workspace is missing. Your Linux image has been preserved." }
        safeDirectory(home, "workspace")
        val adapterDirectory = safeDirectory(root, "usr/local/lib")
        for (name in listOf("niobium-local.cjs", "niobium-agent.cjs")) {
            val stage = Files.createTempFile(adapterDirectory.toPath(), ".niobium-", ".pending")
            try {
                context.assets.open(name).use { input -> Files.newOutputStream(stage).use(input::copyTo) }
                Files.move(stage, File(adapterDirectory, name).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally { Files.deleteIfExists(stage) }
        }
        val skillDirectory = safeDirectory(home, "workspace/.agents/skills/pixel-builder")
        val skill = File(skillDirectory, "SKILL.md")
        if (!Files.exists(skill.toPath(), NOFOLLOW_LINKS)) context.assets.open("bot_build.md").use { input ->
            Files.newOutputStream(skill.toPath(), java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE).use(input::copyTo)
        }
        progress(90)
        return PreparedRuntime(root, home, temporary, libraries, native, image.getString("dsh_version"))
    }

    private fun safeDirectory(base: File, relative: String): File {
        var current = base.toPath()
        check(Files.isDirectory(current, NOFOLLOW_LINKS)) { "The local runtime directory was replaced. Restore it before starting." }
        for (part in relative.split('/')) {
            current = current.resolve(part)
            if (!Files.exists(current, NOFOLLOW_LINKS)) Files.createDirectory(current)
            check(Files.isDirectory(current, NOFOLLOW_LINKS)) { "A local runtime directory was replaced by a link. Restore it before starting." }
        }
        return current.toFile()
    }

    private fun removeTree(directory: File) {
        if (!Files.exists(directory.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        // Files.walk does not follow guest symlinks into Android host directories.
        Files.walk(directory.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun sha(input: java.io.InputStream, cancelled: () -> Boolean): String = input.use {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            if (cancelled()) throw InterruptedException("Runtime preparation cancelled.")
            val count = it.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
    }
}

object RuntimeCommand {
    fun process(runtime: PreparedRuntime, command: List<String>, guestEnvironment: Map<String, String> = emptyMap()): ProcessBuilder {
        val args = mutableListOf(
            File(runtime.native, "libniobium_supervisor.so").path,
            File(runtime.native, "libniobium_proot.so").path,
            "-L", "--link2symlink", "--kill-on-exit", "-0", "--rootfs=" + runtime.root.path, "--cwd=/root/workspace",
            "-b", "/dev", "-b", "/proc", "-b", runtime.home.path + ":/root",
            "/usr/bin/env", "-u", "LD_LIBRARY_PATH", "-u", "LD_PRELOAD",
            "-u", "PROOT_LOADER", "-u", "PROOT_TMP_DIR", "-u", "PROOT_NO_SECCOMP",
            "HOME=/root", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8", "TMPDIR=/tmp"
        )
        args.addAll(command)
        return ProcessBuilder(args).directory(runtime.root).redirectErrorStream(true).apply {
            environment().apply {
                clear()
                put("PROOT_LOADER", File(runtime.native, "libniobium_proot_loader.so").path)
                put("PROOT_TMP_DIR", runtime.temporary.path)
                put("PROOT_NO_SECCOMP", "1")
                put("LD_LIBRARY_PATH", runtime.libraries.path + ":" + runtime.native.path)
                // Credentials travel in the environment, never command arguments or logs.
                putAll(guestEnvironment)
            }
        }
    }
}
