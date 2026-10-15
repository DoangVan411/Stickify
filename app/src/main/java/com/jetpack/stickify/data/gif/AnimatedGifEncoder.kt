package com.jetpack.stickify.data.gif

import android.graphics.Bitmap
import android.graphics.Color
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Animated GIF encoder – Port thuần Kotlin từ Kevin Weiner's Java implementation.
 * Tạo file GIF89a animated mà không cần thư viện bên ngoài.
 *
 * Cách dùng:
 * ```
 * val encoder = AnimatedGifEncoder()
 * encoder.start(outputStream)
 * encoder.setDelay(50)    // 50ms mỗi frame
 * encoder.setRepeat(0)    // lặp vô hạn
 * encoder.addFrame(bitmap1)
 * encoder.addFrame(bitmap2)
 * encoder.finish()
 * ```
 */
class AnimatedGifEncoder {

    private var width = 0
    private var height = 0
    private var transparent: Int = -1
    private var transIndex = 0
    private var repeat = -1 // -1 = no repeat, 0 = infinite
    private var delay = 0 // frame delay (hundredths)
    private var started = false
    private var out: OutputStream? = null
    private var image: Bitmap? = null
    private var pixels: ByteArray? = null
    private var indexedPixels: ByteArray? = null
    private var colorDepth = 0
    private var colorTab: ByteArray? = null
    private var usedEntry = BooleanArray(256)
    private var palSize = 7 // color table size (2^(palSize+1))
    private var dispose = -1
    private var closeStream = false
    private var firstFrame = true
    private var sizeSet = false
    private var sample = 10 // default sample interval for quantizer

    /**
     * Sets the delay time between each frame, or changes it for subsequent frames
     * (applies to last frame added).
     *
     * @param ms delay time in milliseconds
     */
    fun setDelay(ms: Int) {
        delay = (ms / 10f).roundToInt()
    }

    /**
     * Sets the GIF frame disposal code for the last added frame and any
     * subsequent frames. Default is 0 if no transparent color has been set,
     * otherwise 2.
     */
    fun setDispose(code: Int) {
        if (code >= 0) {
            dispose = code
        }
    }

    /**
     * Sets the number of times the set of GIF frames should be played.
     * Default is 1; 0 means play indefinitely.
     */
    fun setRepeat(iter: Int) {
        if (iter >= 0) {
            repeat = iter
        }
    }

    /**
     * Sets the transparent color for the last added frame and any subsequent
     * frames. Since all colors are subject to modification in the quantization
     * process, the color in the final palette for each frame closest to the
     * given color becomes the transparent color for that frame. May be set to
     * null to indicate no transparent color.
     */
    fun setTransparent(c: Int) {
        transparent = c
    }

    /**
     * Adds next GIF frame. The frame is not written immediately, but is
     * actually deferred until the next frame is received so that timing
     * data can be inserted. Calling finish() flushes all frames.
     *
     * @return true if successful
     */
    fun addFrame(im: Bitmap?): Boolean {
        if (im == null || !started) {
            return false
        }
        var ok = true
        try {
            if (!sizeSet) {
                setSize(im.width, im.height)
            }
            image = im
            getImagePixels()
            analyzePixels()
            if (firstFrame) {
                writeLSD()
                writePalette()
                if (repeat >= 0) {
                    writeNetscapeExt()
                }
            }
            writeGraphicCtrlExt()
            writeImageDesc()
            if (!firstFrame) {
                writePalette()
            }
            writePixels()
            firstFrame = false
        } catch (e: IOException) {
            ok = false
        }
        return ok
    }

    /**
     * Initiates GIF file creation on the given stream.
     *
     * @return false if initial write failed
     */
    fun start(os: OutputStream?): Boolean {
        if (os == null) return false
        var ok = true
        closeStream = false
        out = BufferedOutputStream(os)
        try {
            writeString("GIF89a") // header
        } catch (e: IOException) {
            ok = false
        }
        started = ok
        return ok
    }

    /**
     * Flushes any pending data and closes output stream.
     *
     * @return true if stream was flushed successfully
     */
    fun finish(): Boolean {
        if (!started) return false
        var ok = true
        started = false
        try {
            out?.write(0x3b) // GIF trailer
            out?.flush()
            if (closeStream) {
                out?.close()
            }
        } catch (e: IOException) {
            ok = false
        }

        // Reset for subsequent use
        transIndex = 0
        out = null
        image = null
        pixels = null
        indexedPixels = null
        colorTab = null
        closeStream = false
        firstFrame = true

        return ok
    }

    /**
     * Sets frame rate in frames per second.
     */
    fun setFrameRate(fps: Float) {
        if (fps > 0f) {
            delay = (100f / fps).roundToInt()
        }
    }

    /**
     * Sets quality of color quantization (conversion of images to the maximum
     * 256 colors). Lower values (minimum = 1) produce better quality but slower
     * processing. 10 is the default, producing good results at reasonable speed.
     */
    fun setQuality(quality: Int) {
        sample = if (quality < 1) 1 else quality
    }

    /**
     * Sets the GIF frame size. Default size is the size of the first image.
     */
    fun setSize(w: Int, h: Int) {
        if (started && !firstFrame) return
        width = w
        height = h
        if (width < 1) width = 320
        if (height < 1) height = 240
        sizeSet = true
    }

    /**
     * Extracts image pixels into byte array pixels
     */
    private fun getImagePixels() {
        val im = image ?: return
        val w = im.width
        val h = im.height

        if (w != width || h != height) {
            // Resize nhưng giữ nguyên nội dung
            val temp = Bitmap.createScaledBitmap(im, width, height, true)
            val intPixels = IntArray(width * height)
            temp.getPixels(intPixels, 0, width, 0, 0, width, height)
            pixels = ByteArray(intPixels.size * 3)
            for (i in intPixels.indices) {
                val argb = intPixels[i]
                val j = i * 3
                pixels!![j] = Color.red(argb).toByte()
                pixels!![j + 1] = Color.green(argb).toByte()
                pixels!![j + 2] = Color.blue(argb).toByte()
            }
            if (temp != im) temp.recycle()
        } else {
            val intPixels = IntArray(w * h)
            im.getPixels(intPixels, 0, w, 0, 0, w, h)
            pixels = ByteArray(intPixels.size * 3)
            for (i in intPixels.indices) {
                val argb = intPixels[i]
                val j = i * 3
                pixels!![j] = Color.red(argb).toByte()
                pixels!![j + 1] = Color.green(argb).toByte()
                pixels!![j + 2] = Color.blue(argb).toByte()
            }
        }
    }

    /**
     * Analyzes image colors and creates color table.
     */
    private fun analyzePixels() {
        val len = pixels!!.size
        val nPix = len / 3
        indexedPixels = ByteArray(nPix)

        val nq = NeuQuant(pixels!!, len, sample)
        colorTab = nq.process()

        // Map image pixels to new palette
        var k = 0
        for (i in 0 until nPix) {
            val index = nq.map(
                (pixels!![k++].toInt() and 0xff),
                (pixels!![k++].toInt() and 0xff),
                (pixels!![k++].toInt() and 0xff)
            )
            usedEntry[index] = true
            indexedPixels!![i] = index.toByte()
        }
        pixels = null
        colorDepth = 8
        palSize = 7

        // Get closest match to transparent color
        if (transparent != -1) {
            transIndex = findClosest(transparent)
        }
    }

    /**
     * Returns index of palette color closest to c
     */
    private fun findClosest(c: Int): Int {
        val tab = colorTab ?: return -1
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        var minPos = 0
        var dMin = 256 * 256 * 256
        val len = tab.size
        var i = 0
        while (i < len) {
            val dr = r - (tab[i++].toInt() and 0xff)
            val dg = g - (tab[i++].toInt() and 0xff)
            val db = b - (tab[i++].toInt() and 0xff)
            val d = dr * dr + dg * dg + db * db
            val index = (i / 3) - 1
            if (usedEntry[index] && d < dMin) {
                dMin = d
                minPos = index
            }
        }
        return minPos
    }

    /**
     * Writes Graphic Control Extension
     */
    @Throws(IOException::class)
    private fun writeGraphicCtrlExt() {
        val os = out ?: return
        os.write(0x21) // extension introducer
        os.write(0xf9) // GCE label
        os.write(4)    // data block size

        val transp: Int
        var disp: Int
        if (transparent == -1) {
            transp = 0
            disp = 0 // dispose = no action
        } else {
            transp = 1
            disp = 2 // force clear if using transparent color
        }
        if (dispose >= 0) {
            disp = dispose and 7 // user override
        }
        disp = disp shl 2

        // Packed fields
        os.write(
            0 or         // 1:3 reserved
            disp or      // 4:6 disposal
            0 or         // 7   user input - 0 = none
            transp       // 8   transparency flag
        )
        writeShort(delay) // delay x 1/100 sec
        os.write(transIndex) // transparent color index
        os.write(0) // block terminator
    }

    /**
     * Writes Image Descriptor
     */
    @Throws(IOException::class)
    private fun writeImageDesc() {
        val os = out ?: return
        os.write(0x2c)    // image separator
        writeShort(0)     // image position x,y = 0,0
        writeShort(0)
        writeShort(width) // image size
        writeShort(height)

        // packed fields
        if (firstFrame) {
            // no LCT - GCT is used for first (or only) frame
            os.write(0)
        } else {
            // specify normal LCT
            os.write(
                0x80 or // 1 local color table 1=yes
                0 or    // 2 interlace - 0=no
                0 or    // 3 sorted - 0=no
                0 or    // 4-5 reserved
                palSize // 6-8 size of color table
            )
        }
    }

    /**
     * Writes Logical Screen Descriptor
     */
    @Throws(IOException::class)
    private fun writeLSD() {
        // logical screen size
        writeShort(width)
        writeShort(height)
        // packed fields
        out?.write(
            0x80 or // 1   : global color table flag = 1
            0x70 or // 2-4 : color resolution = 7
            0x00 or // 5   : gct sort flag = 0
            palSize // 6-8 : gct size
        )
        out?.write(0) // background color index
        out?.write(0) // pixel aspect ratio - assume 1:1
    }

    /**
     * Writes Netscape application extension to define repeat count.
     */
    @Throws(IOException::class)
    private fun writeNetscapeExt() {
        val os = out ?: return
        os.write(0x21)    // extension introducer
        os.write(0xff)    // app extension label
        os.write(11)      // block size
        writeString("NETSCAPE" + "2.0") // app id + auth code
        os.write(3)       // sub-block size
        os.write(1)       // loop sub-block id
        writeShort(repeat) // loop count (0 = repeat forever)
        os.write(0)       // block terminator
    }

    /**
     * Writes color table
     */
    @Throws(IOException::class)
    private fun writePalette() {
        val tab = colorTab ?: return
        out?.write(tab, 0, tab.size)
        val n = (3 * 256) - tab.size
        for (i in 0 until n) {
            out?.write(0)
        }
    }

    /**
     * Encodes and writes pixel data
     */
    @Throws(IOException::class)
    private fun writePixels() {
        val encoder = LZWEncoder(width, height, indexedPixels!!, colorDepth)
        encoder.encode(out!!)
    }

    @Throws(IOException::class)
    private fun writeShort(value: Int) {
        val os = out ?: return
        os.write(value and 0xff)
        os.write((value shr 8) and 0xff)
    }

    @Throws(IOException::class)
    private fun writeString(s: String) {
        for (c in s) {
            out?.write(c.code)
        }
    }
}

// ============================================================================
// NeuQuant – Neural-Net quantization algorithm
// Based on Anthony Dekker's NeuQuant algorithm
// ============================================================================
internal class NeuQuant(private val thepicture: ByteArray, private val lengthcount: Int, private val samplefac: Int) {

    companion object {
        private const val netsize = 256
        private const val prime1 = 499
        private const val prime2 = 491
        private const val prime3 = 487
        private const val prime4 = 503
        private const val minpicturebytes = 3 * prime4

        private const val maxnetpos = netsize - 1
        private const val netbiasshift = 4
        private const val ncycles = 100

        private const val intbiasshift = 16
        private const val intbias = 1 shl intbiasshift
        private const val gammashift = 10
        private const val gamma = 1 shl gammashift
        private const val betashift = 10
        private const val beta = intbias shr betashift
        private const val betagamma = intbias shl (gammashift - betashift)

        private const val initrad = netsize shr 3
        private const val radiusbiasshift = 6
        private const val radiusbias = 1 shl radiusbiasshift
        private const val initradius = initrad * radiusbias
        private const val radiusdec = 30

        private const val alphabiasshift = 10
        private const val initalpha = 1 shl alphabiasshift

        private const val radbiasshift = 8
        private const val radbias = 1 shl radbiasshift
        private const val alpharadbshift = alphabiasshift + radbiasshift
        private const val alpharadbias = 1 shl alpharadbshift
    }

    private var alphadec = 0
    private val network = Array(netsize) { IntArray(4) }
    private val netindex = IntArray(256)
    private val bias = IntArray(netsize)
    private val freq = IntArray(netsize)
    private val radpower = IntArray(initrad)

    init {
        for (i in 0 until netsize) {
            val v = (i shl (netbiasshift + 8)) / netsize
            network[i][0] = v
            network[i][1] = v
            network[i][2] = v
            freq[i] = intbias / netsize
            bias[i] = 0
        }
    }

    fun process(): ByteArray {
        learn()
        unbiasnet()
        inxbuild()
        return colorMap()
    }

    private fun colorMap(): ByteArray {
        val map = ByteArray(3 * netsize)
        val index = IntArray(netsize)
        for (i in 0 until netsize) {
            index[network[i][3]] = i
        }
        var k = 0
        for (i in 0 until netsize) {
            val j = index[i]
            map[k++] = network[j][0].toByte()
            map[k++] = network[j][1].toByte()
            map[k++] = network[j][2].toByte()
        }
        return map
    }

    private fun inxbuild() {
        var previouscol = 0
        var startpos = 0

        for (i in 0 until netsize) {
            var smallpos = i
            var smallval = network[i][1] // index on g

            for (j in i + 1 until netsize) {
                if (network[j][1] < smallval) {
                    smallpos = j
                    smallval = network[j][1]
                }
            }
            val temp = network[smallpos]
            network[smallpos] = network[i]
            network[i] = temp

            if (smallval != previouscol) {
                netindex[previouscol] = (startpos + i) shr 1
                for (j in previouscol + 1 until smallval) {
                    netindex[j] = i
                }
                previouscol = smallval
                startpos = i
            }
            network[i][3] = i
        }
        netindex[previouscol] = (startpos + maxnetpos) shr 1
        for (j in previouscol + 1..255) {
            netindex[j] = maxnetpos
        }
    }

    private fun learn() {
        if (lengthcount < minpicturebytes) {
            samplefac // ignore
        }
        alphadec = 30 + ((samplefac - 1) / 3)
        val pix = thepicture
        val samplepixels = lengthcount / (3 * samplefac)
        var delta = max(samplepixels / ncycles, 1)
        var alpha = initalpha
        var radius = initradius

        var rad = radius shr radiusbiasshift
        if (rad <= 1) rad = 0
        for (i in 0 until rad) {
            radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad))
        }

        val step = when {
            lengthcount < minpicturebytes -> 3
            (lengthcount % prime1) != 0 -> 3 * prime1
            (lengthcount % prime2) != 0 -> 3 * prime2
            (lengthcount % prime3) != 0 -> 3 * prime3
            else -> 3 * prime4
        }

        var p = 0
        var i = 0
        while (i < samplepixels) {
            val b = (pix[p].toInt() and 0xff) shl netbiasshift
            val g = (pix[p + 1].toInt() and 0xff) shl netbiasshift
            val r = (pix[p + 2].toInt() and 0xff) shl netbiasshift
            val j = contest(b, g, r)

            altersingle(alpha, j, b, g, r)
            if (rad != 0) alterneigh(rad, j, b, g, r)

            p += step
            if (p >= lengthcount) p -= lengthcount
            i++
            if (delta == 0) delta = 1
            if (i % delta == 0) {
                alpha -= alpha / alphadec
                radius -= radius / radiusdec
                rad = radius shr radiusbiasshift
                if (rad <= 1) rad = 0
                for (k in 0 until rad) {
                    radpower[k] = alpha * (((rad * rad - k * k) * radbias) / (rad * rad))
                }
            }
        }
    }

    fun map(b: Int, g: Int, r: Int): Int {
        var bestd = 1000
        var best = -1
        var i = netindex[g]
        var j = i - 1

        while (i < netsize || j >= 0) {
            if (i < netsize) {
                val n = network[i]
                var dist = n[1] - g
                if (dist >= bestd) {
                    i = netsize
                } else {
                    i++
                    if (dist < 0) dist = -dist
                    var a = n[0] - b
                    if (a < 0) a = -a
                    dist += a
                    if (dist < bestd) {
                        a = n[2] - r
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            bestd = dist
                            best = n[3]
                        }
                    }
                }
            }
            if (j >= 0) {
                val n = network[j]
                var dist = g - n[1]
                if (dist >= bestd) {
                    j = -1
                } else {
                    j--
                    if (dist < 0) dist = -dist
                    var a = n[0] - b
                    if (a < 0) a = -a
                    dist += a
                    if (dist < bestd) {
                        a = n[2] - r
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            bestd = dist
                            best = n[3]
                        }
                    }
                }
            }
        }
        return best
    }

    private fun contest(b: Int, g: Int, r: Int): Int {
        var bestd = Int.MAX_VALUE.toLong()
        var bestbiasd = bestd
        var bestpos = -1
        var bestbiaspos = bestpos

        for (i in 0 until netsize) {
            val n = network[i]
            var dist = abs(n[0] - b) + abs(n[1] - g) + abs(n[2] - r)
            if (dist < bestd) {
                bestd = dist.toLong()
                bestpos = i
            }
            val biasdist = dist - ((bias[i]) shr (intbiasshift - netbiasshift))
            if (biasdist < bestbiasd) {
                bestbiasd = biasdist.toLong()
                bestbiaspos = i
            }
            val betafreq = (freq[i] shr betashift)
            freq[i] -= betafreq
            bias[i] += (betafreq shl gammashift)
        }
        freq[bestpos] += beta
        bias[bestpos] -= betagamma
        return bestbiaspos
    }

    private fun altersingle(alpha: Int, i: Int, b: Int, g: Int, r: Int) {
        val n = network[i]
        n[0] -= (alpha * (n[0] - b)) / initalpha
        n[1] -= (alpha * (n[1] - g)) / initalpha
        n[2] -= (alpha * (n[2] - r)) / initalpha
    }

    private fun alterneigh(rad: Int, i: Int, b: Int, g: Int, r: Int) {
        val lo = max(i - rad, -1)
        val hi = if (i + rad > netsize) netsize else i + rad

        var j = i + 1
        var k = i - 1
        var m = 1
        while (j < hi || k > lo) {
            val a = radpower[m++]
            if (j < hi) {
                val n = network[j++]
                n[0] -= (a * (n[0] - b)) / alpharadbias
                n[1] -= (a * (n[1] - g)) / alpharadbias
                n[2] -= (a * (n[2] - r)) / alpharadbias
            }
            if (k > lo) {
                val n = network[k--]
                n[0] -= (a * (n[0] - b)) / alpharadbias
                n[1] -= (a * (n[1] - g)) / alpharadbias
                n[2] -= (a * (n[2] - r)) / alpharadbias
            }
        }
    }

    private fun unbiasnet() {
        for (i in 0 until netsize) {
            network[i][0] = network[i][0] shr netbiasshift
            network[i][1] = network[i][1] shr netbiasshift
            network[i][2] = network[i][2] shr netbiasshift
            network[i][3] = i
        }
    }
}

// ============================================================================
// LZW encoder for GIF
// Based on UNIX compress and Jef Poskanzer's implementation
// ============================================================================
internal class LZWEncoder(
    private val imgW: Int,
    private val imgH: Int,
    private val pixAry: ByteArray,
    private val initCodeSize: Int
) {

    companion object {
        private const val EOF = -1
        private const val BITS = 12
        private const val HSIZE = 5003
        private val masks = intArrayOf(
            0x0000, 0x0001, 0x0003, 0x0007, 0x000F,
            0x001F, 0x003F, 0x007F, 0x00FF,
            0x01FF, 0x03FF, 0x07FF, 0x0FFF,
            0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF
        )
    }

    private var curPixel = 0
    private var remaining = 0

    // GIF specific
    private var nBits = 0
    private var maxbits = BITS
    private var maxcode = 0
    private var maxmaxcode = 1 shl BITS

    private val htab = IntArray(HSIZE)
    private val codetab = IntArray(HSIZE)

    private var hsize = HSIZE

    private var freeEnt = 0
    private var clearFlg = false
    private var gInitBits = 0
    private var clearCode = 0
    private var eofCode = 0

    // Output
    private var curAccum = 0
    private var curBits = 0
    private val accum = ByteArray(256)
    private var aCount = 0

    fun encode(os: OutputStream) {
        os.write(initCodeSize) // write "initial code size" byte
        remaining = imgW * imgH
        curPixel = 0
        compress(initCodeSize + 1, os)
        os.write(0) // write block terminator
    }

    private fun compress(initBits: Int, outs: OutputStream) {
        gInitBits = initBits
        clearFlg = false
        nBits = gInitBits
        maxcode = maxCode(nBits)

        clearCode = 1 shl (initBits - 1)
        eofCode = clearCode + 1
        freeEnt = clearCode + 2

        aCount = 0

        var ent = nextPixel()
        var hshift = 0
        var fcode: Int
        var hsize_reg = hsize
        while (hsize_reg < 65536) {
            hshift++
            hsize_reg *= 2
        }
        hsize_reg = hsize
        hshift = 8 - hshift

        clHash(hsize_reg)
        output(clearCode, outs)

        var c: Int
        outer@ while (true) {
            c = nextPixel()
            if (c == EOF) break
            fcode = (c shl maxbits) + ent
            var i = (c shl hshift) xor ent

            if (htab[i] == fcode) {
                ent = codetab[i]
                continue
            } else if (htab[i] >= 0) {
                var disp = hsize_reg - i
                if (i == 0) disp = 1
                do {
                    i -= disp
                    if (i < 0) i += hsize_reg
                    if (htab[i] == fcode) {
                        ent = codetab[i]
                        continue@outer
                    }
                } while (htab[i] >= 0)
            }
            output(ent, outs)
            ent = c
            if (freeEnt < maxmaxcode) {
                codetab[i] = freeEnt++
                htab[i] = fcode
            } else {
                clBlock(outs)
            }
        }
        output(ent, outs)
        output(eofCode, outs)
    }

    private fun output(code: Int, outs: OutputStream) {
        curAccum = curAccum and masks[curBits]
        curAccum = if (curBits > 0) curAccum or (code shl curBits) else code
        curBits += nBits

        while (curBits >= 8) {
            charOut((curAccum and 0xff).toByte(), outs)
            curAccum = curAccum shr 8
            curBits -= 8
        }

        if (freeEnt > maxcode || clearFlg) {
            if (clearFlg) {
                maxcode = maxCode(gInitBits.also { nBits = it })
                clearFlg = false
            } else {
                nBits++
                maxcode = if (nBits == maxbits) maxmaxcode else maxCode(nBits)
            }
        }

        if (code == eofCode) {
            while (curBits > 0) {
                charOut((curAccum and 0xff).toByte(), outs)
                curAccum = curAccum shr 8
                curBits -= 8
            }
            flushChar(outs)
        }
    }

    private fun clBlock(outs: OutputStream) {
        clHash(hsize)
        freeEnt = clearCode + 2
        clearFlg = true
        output(clearCode, outs)
    }

    private fun clHash(hSize: Int) {
        for (i in 0 until hSize) {
            htab[i] = -1
        }
    }

    private fun maxCode(nBits: Int): Int = (1 shl nBits) - 1

    private fun nextPixel(): Int {
        if (remaining == 0) return EOF
        remaining--
        val pix = pixAry[curPixel++].toInt() and 0xff
        return pix
    }

    private fun charOut(c: Byte, outs: OutputStream) {
        accum[aCount++] = c
        if (aCount >= 254) {
            flushChar(outs)
        }
    }

    private fun flushChar(outs: OutputStream) {
        if (aCount > 0) {
            outs.write(aCount)
            outs.write(accum, 0, aCount)
            aCount = 0
        }
    }
}
