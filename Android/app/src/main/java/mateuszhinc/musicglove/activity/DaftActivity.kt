package mateuszhinc.musicglove.activity

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.media.MediaPlayer
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_daft.*
import mateuszhinc.musicglove.R
import mateuszhinc.musicglove.helpers.Const
import java.io.InputStream
import java.io.OutputStream
import java.lang.StringBuilder
import java.util.concurrent.TimeUnit

class DaftActivity : AppCompatActivity() {

    private val subscriptions = CompositeDisposable()

    lateinit var mps: Array<MediaPlayer>
    lateinit var demo: MediaPlayer
    private val sources = arrayOf(R.raw.work_it,
            R.raw.make_it,
            R.raw.do_it,
            R.raw.makes_us,
            R.raw.harder,
            R.raw.better,
            R.raw.faster,
            R.raw.stronger)
    private val labels = arrayOf("Work it",
            "Make it",
            "Do it",
            "Makes us",
            "Harder",
            "Better",
            "Faster",
            "Stronger")

    private val readSubject: PublishSubject<Any> = PublishSubject.create()
    private val clickedSubject: PublishSubject<Int> = PublishSubject.create()
    private val connectSubject: PublishSubject<Boolean> = PublishSubject.create()
    private val statusSubject: PublishSubject<String> = PublishSubject.create()
    private val errorSubject: PublishSubject<Throwable> = PublishSubject.create()

    private val timerTick = Observable.interval(500, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.computation())

    private var bluetooth: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private var isBtConnected = false
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isConnecting = false

    private lateinit var buttons: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daft)
        val device = intent.getParcelableExtra<BluetoothDevice>(Const.INTENT_EXTRA_BT_DEVICE)!!
        bluetooth = BluetoothAdapter.getDefaultAdapter()

        mps = sources.map { MediaPlayer.create(this, it) }.toTypedArray()
        demo = MediaPlayer.create(this, R.raw.demo)
        buttons = listOf(view_button0,
                view_button1,
                view_button2,
                view_button3,
                view_button4,
                view_button5,
                view_button6,
                view_button7)

        buttons.forEachIndexed { index, button ->
            mps[index].setOnCompletionListener { button.background = getDrawable(android.R.color.holo_red_dark) }
            button.text = labels[index]
        }

        subscriptions.addAll(
                readSubject
                        .observeOn(Schedulers.io())
                        .takeWhile { btSocket != null && btSocket!!.isConnected && inputStream != null && isBtConnected }
                        .subscribe({
                            val buffer: ByteArray = kotlin.ByteArray(64)
                            var bytes: Int
                            val read = StringBuilder()
                            do {
                                bytes = inputStream!!.read(buffer)
                                read.append(String(buffer, 0, bytes))
                            } while (!read.endsWith(';'))
                            val result = read.toString()
                            val note = result[result.length - 2].toInt() - '0'.toInt()
                            clickedSubject.onNext(note)
                            readSubject.onNext(Any())
                        }, {
                            System.err.println(it.message)
                        }),
                timerTick
                        .observeOn(Schedulers.io())
                        .filter { btSocket != null && btSocket!!.isConnected && outputStream != null && isBtConnected }
                        .subscribe({
                            outputStream?.write(1)
                        }, {
                            System.err.println(it.message)
                        }),
                clickedSubject
                        .observeOn(AndroidSchedulers.mainThread())
                        .skipWhile { mps[it].isPlaying || demo.isPlaying }
                        .subscribe({
                            buttons[it].background = getDrawable(android.R.color.holo_red_light)
                            mps[it].start()
                        }, {
                            System.err.println(it.message)
                        }),
                RxView.clicks(view_logo)
                        .subscribe {
                            if(demo.isPlaying){
                                demo.stop()
                                demo.prepare()
                            }else{
                                demo.start()
                            }
                        },
                *buttons.mapIndexed { index, button ->
                    RxView.clicks(button).subscribe { clickedSubject.onNext(index) }
                }.toTypedArray(),
                timerTick
                        .takeWhile { isConnecting }
                        .subscribe {
                            statusSubject.onNext("Connecting" + when (it.rem(3)) {
                                0L -> ".  "
                                1L -> " . "
                                2L -> "  ."
                                else -> "..."
                            })
                        },
                statusSubject
                        .startWith("Connecting   ")
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(RxTextView.text(view_text_status)),
                connectSubject
                        .observeOn(Schedulers.io())
                        .flatMap {
                            isConnecting = true
                            bluetooth?.cancelDiscovery()
                            Observable.just(Any())
                                    .map {
                                        btSocket?.close()
                                        btSocket = device.createInsecureRfcommSocketToServiceRecord(Const.UUID)
                                        btSocket?.connect()
                                        true
                                    }
                                    .retry()
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            isConnecting = false
                            statusSubject.onNext("")
                            isBtConnected = true
                            inputStream = btSocket?.inputStream
                            outputStream = btSocket?.outputStream
                            readSubject.onNext(Any())
                        }, {
                            isBtConnected = false
                            errorSubject.onNext(it)
                        }),
                errorSubject
                        .subscribe {
                            System.err.println(it.message)
                        }
        )

        connectSubject.onNext(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        for (mp in mps) {
            mp.stop()
            mp.release()
        }
        demo.release()
        outputStream?.write(2)
        btSocket?.close()
        subscriptions.clear()
    }
}
