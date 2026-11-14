package gr.laouto.dromoi

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

class MainActivity : Activity() {
 private val names = arrayOf("Ντο","Ντο♯","Ρε","Μι♭","Μι","Φα","Φα♯","Σολ","Σολ♯","Λα","Σι♭","Σι")
 private val roads = linkedMapOf(
  "Χιτζάζ" to intArrayOf(0,1,4,5,7,8,10),
  "Ουσάκ" to intArrayOf(0,1,3,5,7,8,10),
  "Σαμπάχ" to intArrayOf(0,1,3,4,7,8,10),
  "Νιαβέντ" to intArrayOf(0,2,3,5,7,8,11),
  "Ραστ" to intArrayOf(0,2,4,5,7,9,10),
  "Χουζάμ" to intArrayOf(0,1,4,5,7,8,11),
  "Ματζόρε" to intArrayOf(0,2,4,5,7,9,11),
  "Μινόρε" to intArrayOf(0,2,3,5,7,8,10))
 private lateinit var tonic: Spinner
 private lateinit var road: Spinner
 private lateinit var tolerance: SeekBar
 private lateinit var toleranceText: TextView
 private lateinit var current: TextView
 private lateinit var history: LinearLayout
 private lateinit var start: Button
 private val active = AtomicBoolean(false)
 private val ui = Handler(Looper.getMainLooper())
 private var worker: Thread? = null
 private var stableNote = -1
 private var stableSince = 0L
 private var loggedNote = -1
 private var lastSound = 0L
 private var thresholdMs = 200
 private var selectedTonic = 2
 private var selectedRoad = "Χιτζάζ"
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val scroll = ScrollView(this)
  val column = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,32,28,32) }
  scroll.addView(column)
  fun title(s:String, size:Float=18f):TextView = TextView(this).apply { text=s; textSize=size; setTextColor(Color.BLACK); setPadding(0,12,0,12); column.addView(this) }
  title("Λαούτο — Έλεγχος δρόμων",24f)
  title("Τονική")
  tonic = Spinner(this).also { it.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,names); it.setSelection(2); column.addView(it) }
  title("Δρόμος")
  road = Spinner(this).also { it.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,roads.keys.toList()); column.addView(it) }
  val changed=object:android.widget.AdapterView.OnItemSelectedListener {
   override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
   override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) {
    selectedTonic=tonic.selectedItemPosition; selectedRoad=road.selectedItem?.toString() ?: "Χιτζάζ"
    resetRecognition()
   }
  }
  tonic.onItemSelectedListener=changed; road.onItemSelectedListener=changed
  toleranceText=title("Σταθερότητα νότας: 200 ms")
  tolerance=SeekBar(this).apply { max=18; progress=2; column.addView(this) }
  tolerance.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
   override fun onProgressChanged(bar:SeekBar?,p:Int,fromUser:Boolean) { thresholdMs=100+p*50; toleranceText.text="Σταθερότητα νότας: $thresholdMs ms" }
   override fun onStartTrackingTouch(bar:SeekBar?) {}
   override fun onStopTrackingTouch(bar:SeekBar?) {}
  })
  start=Button(this).apply { text="Έναρξη μικροφώνου"; column.addView(this); setOnClickListener { if(active.get()) stopAudio() else requestAudio() } }
  current=title("Παίξε μία καθαρή νότα",25f)
  title("Ιστορικό (μόνο για αυτή την εκκίνηση)")
  history=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; column.addView(this) }
  val clear=Button(this).apply { text="Καθαρισμός ιστορικού"; column.addView(this); setOnClickListener { history.removeAllViews(); resetRecognition() } }
  title("Η αναγνώριση αφορά μία νότα κάθε φορά. Οι δρόμοι είναι συγκερασμένες προσεγγίσεις, όχι έλεγχος μελωδικής πορείας.",13f)
  setContentView(scroll)
 }
 private fun resetRecognition() { stableNote=-1; loggedNote=-1; stableSince=0L }
 private fun requestAudio() {
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
   requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),7)
  else startAudio()
 }
 override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==7 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) startAudio()
 }
 private fun startAudio() {
  if(active.getAndSet(true)) return
  start.text="Διακοπή μικροφώνου"
  worker=Thread {
   val rate=22050
   val n=4096
   val recorder=try { AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,n*4) } catch(e:Exception){ null }
   if(recorder==null || recorder.state!=AudioRecord.STATE_INITIALIZED) {
    active.set(false); ui.post { start.text="Έναρξη μικροφώνου"; current.text="Αδυναμία πρόσβασης στο μικρόφωνο" }; return@Thread
   }
   val samples=ShortArray(n)
   try {
    recorder.startRecording()
    while(active.get()) {
     val count=recorder.read(samples,0,n)
     if(count<2048) continue
     val midi=detect(samples,count,rate)
     val now=System.currentTimeMillis()
     if(midi==null) {
      if(now-lastSound>300) { stableNote=-1; loggedNote=-1 }
      continue
     }
     lastSound=now
     val pitchClass=((midi%12)+12)%12
     if(pitchClass!=stableNote) { stableNote=pitchClass; stableSince=now; loggedNote=-1 }
     if(now-stableSince>=thresholdMs && loggedNote!=pitchClass) {
      loggedNote=pitchClass
      val allowed=roads[selectedRoad]!!.any { (selectedTonic+it)%12==pitchClass }
      ui.post {
       current.text=names[pitchClass]+if(allowed) " — Εντός δρόμου" else " — Εκτός δρόμου"
       current.setTextColor(if(allowed) Color.rgb(25,125,55) else Color.RED)
       val entry=TextView(this).apply {
        text=(if(allowed) "● " else "● ")+names[pitchClass]+if(allowed) " — Εντός" else " — Εκτός"
        textSize=18f; setTextColor(if(allowed) Color.rgb(25,125,55) else Color.RED); setPadding(0,8,0,8)
       }
       history.addView(entry,0)
      }
     }
    }
   } catch(e:Exception) { ui.post { current.text="Σφάλμα μικροφώνου: ${e.message}" } }
   finally { try { recorder.stop() } catch(_:Exception){}; recorder.release(); active.set(false); ui.post { start.text="Έναρξη μικροφώνου" } }
  }.apply { start() }
 }
 private fun detect(a:ShortArray,count:Int,rate:Int):Int? {
  var energy=0.0
  for(i in 0 until count) energy+=a[i].toDouble()*a[i]
  if(sqrt(energy/count)<450.0) return null
  // Normalized autocorrelation; monophonic instrument, 80–1000 Hz.
  val minLag=rate/1000; val maxLag=rate/80
  var bestLag=0; var bestScore=0.0
  for(lag in minLag..maxLag) {
   var dot=0.0; var aa=0.0; var bb=0.0
   var i=0
   while(i<count-lag) {
    val x=a[i].toDouble(); val y=a[i+lag].toDouble()
    dot+=x*y; aa+=x*x; bb+=y*y; i+=2
   }
   val score=dot/sqrt(aa*bb+1.0)
   if(score>bestScore) { bestScore=score; bestLag=lag }
  }
  if(bestScore<0.80 || bestLag==0) return null
  val hz=rate.toDouble()/bestLag
  val midi=69+12*log2(hz/440.0)
  if(abs(midi-round(midi))>0.30) return null
  return round(midi).toInt()
 }
 private fun stopAudio() { active.set(false); start.text="Έναρξη μικροφώνου" }
 override fun onDestroy() { stopAudio(); super.onDestroy() }
}
