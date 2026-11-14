package com.kutlusoy.proiptv.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Runs only where libVLC is installed (PROIPTV_VLC_DIR) and ffmpeg exists. Proves that pictures arrive through the video callbacks. */
class VlcEngineTest {
    @Test
    fun picturesArriveInMemoryWithoutAWindow() {
        assumeTrue(System.getenv("PROIPTV_VLC_DIR") != null)
        val video = File.createTempFile("proiptv", ".mp4")
        val made = runCatching {
            ProcessBuilder("ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=size=320x180:rate=10:duration=3", "-pix_fmt", "yuv420p", video.absolutePath)
                .redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor() == 0
        }.getOrDefault(false)
        assumeTrue(made)
        val engine = VlcEngine()
        assumeTrue("libVLC not available: ${engine.error}", engine.available)
        try {
            engine.play(Playable("test", "file://" + video.absolutePath, null, false), 0)
            var frame: Frame? = null
            val end = System.currentTimeMillis() + 10_000
            while (frame == null && System.currentTimeMillis() < end) {
                Thread.sleep(100)
                frame = engine.frame.get()
            }
            assertTrue("no picture arrived", frame != null)
            assertEquals(320, frame!!.width)
            // libVLC may pad the height to the block size of the decoder
            assertTrue("height ${frame.height}", frame.height in 180..200)
            assertEquals(frame.width * frame.height * 4, frame.pixels.size)
            // the test picture is colourful, a black or empty picture would mean the callbacks deliver nothing
            val nonBlack = frame.pixels.count { it.toInt() != 0 }
            assertTrue("picture is empty", nonBlack > frame.pixels.size / 10)
            // the same conversion the screen uses: a raster image that is not black
            val image = org.jetbrains.skia.Image.makeRaster(
                org.jetbrains.skia.ImageInfo(frame.width, frame.height, org.jetbrains.skia.ColorType.BGRA_8888, org.jetbrains.skia.ColorAlphaType.OPAQUE),
                frame.pixels, frame.width * 4,
            )
            val bitmap = org.jetbrains.skia.Bitmap()
            assertTrue(bitmap.allocPixels(org.jetbrains.skia.ImageInfo(frame.width, frame.height, org.jetbrains.skia.ColorType.BGRA_8888, org.jetbrains.skia.ColorAlphaType.OPAQUE)))
            assertTrue(image.readPixels(bitmap))
            val read = bitmap.readPixels(org.jetbrains.skia.ImageInfo(frame.width, frame.height, org.jetbrains.skia.ColorType.BGRA_8888, org.jetbrains.skia.ColorAlphaType.OPAQUE), frame.width * 4)!!
            assertTrue("image is black", read.count { it.toInt() != 0 } > read.size / 10)
        } finally {
            engine.release()
            video.delete()
        }
    }

    @Test
    fun pictureArrivesWhenTheStreamComesFromTheSharedBuffer() {
        assumeTrue(System.getenv("PROIPTV_VLC_DIR") != null)
        val video = File.createTempFile("proiptv", ".ts")
        val made = runCatching {
            ProcessBuilder("ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=size=320x180:rate=10:duration=4", "-pix_fmt", "yuv420p", "-c:v", "mpeg2video", "-f", "mpegts", video.absolutePath)
                .redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor() == 0
        }.getOrDefault(false)
        assumeTrue(made)
        val engine = VlcEngine()
        assumeTrue("libVLC not available: ${engine.error}", engine.available)
        val bufferFile = File.createTempFile("proiptv", ".buf")
        val buffer = com.kutlusoy.proiptv.engine.RingBuffer(bufferFile, 8L * 1024 * 1024)
        try {
            val bytes = video.readBytes()
            buffer.write(bytes, 0, bytes.size)
            engine.playReader(com.kutlusoy.proiptv.engine.HubReader(buffer, 0), "test", 0)
            var frame: Frame? = null
            val end = System.currentTimeMillis() + 10_000
            while (frame == null && System.currentTimeMillis() < end) {
                Thread.sleep(100)
                frame = engine.frame.get()
            }
            assertTrue("no picture arrived", frame != null)
            assertEquals(320, frame!!.width)
            assertTrue("picture is empty", frame.pixels.count { it.toInt() != 0 } > frame.pixels.size / 10)
        } finally {
            engine.stop()
            buffer.close()
            engine.release()
            video.delete()
            bufferFile.delete()
        }
    }
}
