package com.fishbox.videoexport

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.media.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.view.*
import android.Manifest
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var exportButton: Button
    private var inputUri: Uri? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var cancelRequested = false
    private var lastSettings = Settings()

    data class Settings(var invert: Boolean = false, var dither: Boolean = true, var threshold: Int = 128, var fit: Boolean = true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 18, 22, 18); setBackgroundColor(Color.BLACK) }
        val title = TextView(this).apply { text = "FISHBOX  /  VIDEO EXPORT"; textSize = 22f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        val sub = TextView(this).apply { text = "Video → 240×240 Braille logic → TFT dots → 1080×1080 MP4"; textSize = 12f; setTextColor(0xFFAAAAAA.toInt()); setPadding(0, 6, 0, 16) }
        root.addView(sub)

        preview = ImageView(this).apply { setBackgroundColor(Color.BLACK); scaleType = ImageView.ScaleType.FIT_CENTER }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))

        val choose = Button(this).apply { text = "SELECT VIDEO"; setOnClickListener { pickVideo() } }
        root.addView(choose)

        val options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 4) }
        val invert = CheckBox(this).apply { text = "Invert (bright areas become TFT dots)"; setTextColor(Color.WHITE); isChecked = false }
        val dither = CheckBox(this).apply { text = "Floyd–Steinberg dithering"; setTextColor(Color.WHITE); isChecked = true }
        options.addView(invert); options.addView(dither)
        root.addView(options)

        val thresholdLabel = TextView(this).apply { setTextColor(Color.WHITE); text = "Threshold: 128" }
        root.addView(thresholdLabel)
        val threshold = SeekBar(this).apply { max = 255; progress = 128 }
        threshold.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { thresholdLabel.text = "Threshold: $p"; if (inputUri != null) renderPreview() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(threshold)

        val fit = CheckBox(this).apply { text = "Fit video inside 240×240 (otherwise crop)"; setTextColor(Color.WHITE); isChecked = true }
        root.addView(fit)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        exportButton = Button(this).apply { text = "EXPORT 1080×1080 MP4"; isEnabled = false }
        val cancel = Button(this).apply { text = "CANCEL"; isEnabled = false }
        row.addView(exportButton, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(cancel, LinearLayout.LayoutParams(0, -2, 0.55f))
        root.addView(row)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        root.addView(progress)
        status = TextView(this).apply { text = "Select a video to begin."; textSize = 12f; setTextColor(0xFFAAAAAA.toInt()); setPadding(0, 8, 0, 0) }
        root.addView(status)
        setContentView(root)

        exportButton.setOnClickListener {
            lastSettings = Settings(invert.isChecked, dither.isChecked, threshold.progress, fit.isChecked)
            startExport()
        }
        cancel.setOnClickListener { cancelRequested = true; status.text = "Cancelling…" }
        this.cancelButton = cancel
        this.invertBox = invert
        this.ditherBox = dither
        this.thresholdBar = threshold
        this.fitBox = fit
    }

    private lateinit var cancelButton: Button
    private lateinit var invertBox: CheckBox
    private lateinit var ditherBox: CheckBox
    private lateinit var thresholdBar: SeekBar
    private lateinit var fitBox: CheckBox

    private fun pickVideo() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "video/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        startActivityForResult(i, 1001)
    }

    @Deprecated("legacy callback used for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data?.data != null) {
            inputUri = data.data
            try { contentResolver.takePersistableUriPermission(inputUri!!, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            exportButton.isEnabled = true
            status.text = "Ready: ${displayName(inputUri!!)}"
            renderPreview()
        }
    }

    private fun displayName(uri: Uri): String {
        var name = "video"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
        return name
    }

    private fun renderPreview() {
        val uri = inputUri ?: return
        val settings = Settings(invertBox.isChecked, ditherBox.isChecked, thresholdBar.progress, fitBox.isChecked)
        executor.execute {
            try {
                val r = MediaMetadataRetriever(); r.setDataSource(this, uri)
                val b = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST) ?: throw Exception("Could not decode video")
                val out = BrailleProcessor.process(b, settings)
                r.release(); b.recycle()
                main.post { preview.setImageBitmap(out); status.text = "Preview ready · logical 240×240 · output 1080×1080" }
            } catch (e: Exception) { main.post { status.text = "Preview error: ${e.message}" } }
        }
    }

    private fun startExport() {
        if (android.os.Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 2001)
            status.text = "Allow storage access, then press EXPORT again."
            return
        }
        val uri = inputUri ?: return
        cancelRequested = false
        exportButton.isEnabled = false; cancelButton.isEnabled = true
        progress.progress = 0; status.text = "Preparing encoder…"
        executor.execute { VideoExporter(this, uri, lastSettings) { p, text -> main.post { progress.progress = p; status.text = text } }.run({ file -> main.post { finishExport(file) } }, { err -> main.post { failExport(err) } }, { main.post { status.text = "Cancelled"; exportButton.isEnabled = true; cancelButton.isEnabled = false } }, { cancelRequested }) }
    }

    private fun finishExport(file: File) {
        exportButton.isEnabled = true; cancelButton.isEnabled = false; progress.progress = 100
        try {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FishBox")
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                file.delete()
                status.text = "DONE · 1080×1080 MP4\nSaved to Movies/FishBox"
            } else status.text = "DONE · ${file.name}"
        } catch (e: Exception) {
            status.text = "DONE · ${file.name}\nPrivate export saved; gallery copy failed."
        }
        Toast.makeText(this, "Export complete", Toast.LENGTH_LONG).show()
    }
    private fun failExport(t: Throwable) { exportButton.isEnabled = true; cancelButton.isEnabled = false; status.text = "Export failed: ${t.message}" }

    override fun onDestroy() { cancelRequested = true; executor.shutdownNow(); super.onDestroy() }
}

object BrailleProcessor {
    private val dots = intArrayOf(0, 1, 2, 6, 3, 4, 5, 7)

    fun process(src: Bitmap, s: MainActivity.Settings): Bitmap {
        val work = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val c = Canvas(work)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }
        val dst = Rect(0, 0, 240, 240)
        if (s.fit) {
            val scale = minOf(240f/src.width, 240f/src.height); val w=(src.width*scale).roundToInt(); val h=(src.height*scale).roundToInt()
            val left=(240-w)/2; val top=(240-h)/2; c.drawColor(Color.BLACK); c.drawBitmap(src, null, Rect(left,top,left+w,top+h), paint)
        } else { val scale=maxOf(240f/src.width, 240f/src.height); val w=(src.width*scale).roundToInt(); val h=(src.height*scale).roundToInt(); c.drawBitmap(src,null,Rect((240-w)/2,(240-h)/2,(240+w)/2,(240+h)/2),paint) }
        val pixels = FloatArray(240*240)
        val px = IntArray(240*240); work.getPixels(px,0,240,0,0,240,240)
        for (i in px.indices) { val col=px[i]; var lum=.22f*Color.red(col)+.72f*Color.green(col)+.06f*Color.blue(col); if(s.invert) lum=255f-lum; pixels[i]=lum }
        val bw = BooleanArray(240*240)
        if (s.dither) {
            for(y in 0 until 240) for(x in 0 until 240) { val i=y*240+x; val old=pixels[i]; val on=old >= s.threshold; bw[i]=on; val err=old-(if(on)255f else 0f); if(x+1<240)pixels[i+1]+=err*7/16; if(x>0 && y+1<240)pixels[i+239]+=err*3/16; if(y+1<240)pixels[i+240]+=err*5/16; if(x+1<240&&y+1<240)pixels[i+241]+=err/16 }
        } else for(i in pixels.indices) bw[i]=pixels[i]>=s.threshold
        // Exact 2×4 Unicode Braille mapping, reconstructed as the actual 240×240 TFT dots.
        val out=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888); out.eraseColor(Color.BLACK); val oc=Canvas(out); val op=Paint().apply{color=Color.WHITE;style=Paint.Style.FILL}
        for(by in 0 until 240 step 4) for(bx in 0 until 240 step 2) { var mask=0; for(k in 0..7){ val yy=by+(if(k<3) k else if(k==3)3 else if(k<6)k-3 else 3); val xx=bx+(if(k<=3)0 else 1); if(yy<240&&xx<240&&bw[yy*240+xx]) mask=mask or (1 shl dots[k]) }; for(k in 0..7) if((mask and (1 shl dots[k]))!=0){ val yy=by+(if(k<3)k else if(k==3)3 else if(k<6)k-3 else 3); val xx=bx+(if(k<=3)0 else 1); oc.drawRect(xx.toFloat(),yy.toFloat(),xx+1f,yy+1f,op) } }
        work.recycle(); return out
    }
}

class VideoExporter(private val activity: Activity, private val uri: Uri, private val settings: MainActivity.Settings, private val update: (Int,String)->Unit) {
    private val W=1080
    private val H=1080

    fun run(done:(File)->Unit, fail:(Throwable)->Unit, cancelled:()->Unit, isCancelled:()->Boolean) {
        var retriever: MediaMetadataRetriever?=null
        var codec: MediaCodec?=null
        var muxer: MediaMuxer?=null
        try {
            retriever=MediaMetadataRetriever()
            retriever.setDataSource(activity,uri)
            val durationUs=(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)*1000L
            val fps=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull()?.takeIf{it>0} ?: 30.0
            val total=maxOf(1,(durationUs/1_000_000.0*fps).roundToInt())
            val out=File(activity.getExternalFilesDir("Movies"),"FishBox_${System.currentTimeMillis()}.mp4")
            val format=MediaFormat.createVideoFormat("video/avc",W,H).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE,5_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE,fps.roundToInt().coerceIn(1,60))
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
            }
            codec=MediaCodec.createEncoderByType("video/avc")
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface=codec.createInputSurface()
            codec.start()
            muxer=MediaMuxer(out.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track=-1
            var muxerStarted=false
            val info=MediaCodec.BufferInfo()
            val paint=Paint().apply{isFilterBitmap=false}

            fun drain(endOfStream:Boolean=false):Boolean {
                while(true) {
                    val index=codec!!.dequeueOutputBuffer(info,10_000)
                    when {
                        index==MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                        index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if(muxerStarted) throw IllegalStateException("Encoder format changed twice")
                            track=muxer!!.addTrack(codec!!.outputFormat)
                            muxer!!.start(); muxerStarted=true
                        }
                        index>=0 -> {
                            if((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0) info.size=0
                            if(info.size>0 && muxerStarted) {
                                val buffer=codec!!.getOutputBuffer(index)!!
                                buffer.position(info.offset); buffer.limit(info.offset+info.size)
                                muxer!!.writeSampleData(track,buffer,info)
                            }
                            val eos=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
                            codec!!.releaseOutputBuffer(index,false)
                            if(eos) return true
                        }
                    }
                }
            }

            for(n in 0 until total) {
                if(isCancelled()) { codec.signalEndOfInputStream(); while(!drain()){}; try{muxer?.release()}catch(_:Throwable){}; try{codec.stop()}catch(_:Throwable){}; try{codec.release()}catch(_:Throwable){}; try{retriever.release()}catch(_:Throwable){}; return cancelled() }
                val tUs=minOf(maxOf(0,durationUs-1),(n/fps*1_000_000.0).toLong())
                val src=retriever.getFrameAtTime(tUs,MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                val logical=BrailleProcessor.process(src,settings)
                src.recycle()
                val canvas=surface.lockCanvas(null)
                try {
                    canvas.drawColor(Color.BLACK)
                    canvas.drawBitmap(logical,null,Rect(0,0,W,H),paint)
                } finally { surface.unlockCanvasAndPost(canvas) }
                logical.recycle()
                drain()
                update((n*100/total).coerceAtMost(99),"Encoding frame ${n+1} / $total")
            }
            codec.signalEndOfInputStream()
            while(!drain(true)) {}
            if(muxerStarted) muxer.stop()
            muxer.release(); muxer=null
            codec.stop(); codec.release(); codec=null
            retriever.release(); retriever=null
            done(out)
        } catch(t:Throwable) {
            try{codec?.stop()}catch(_:Throwable){}
            try{codec?.release()}catch(_:Throwable){}
            try{retriever?.release()}catch(_:Throwable){}
            try{muxer?.release()}catch(_:Throwable){}
            fail(t)
        }
    }
}
