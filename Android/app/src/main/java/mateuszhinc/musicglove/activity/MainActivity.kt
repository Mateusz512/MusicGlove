package mateuszhinc.musicglove.activity

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.annotation.NonNull
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_main.*
import mateuszhinc.musicglove.helpers.Const.INTENT_EXTRA_BT_DEVICE
import mateuszhinc.musicglove.R
import mateuszhinc.musicglove.adapter.DeviceListAdapter
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    val discoveredSubject: PublishSubject<BluetoothDevice> = PublishSubject.create()
    private val showProgressSubject: PublishSubject<Boolean> = PublishSubject.create()
    private val enableButtonSubject: PublishSubject<Boolean> = PublishSubject.create()
    private val finishScanSubject: PublishSubject<Any> = PublishSubject.create()
    private val rowClickSubject: PublishSubject<BluetoothDevice> = PublishSubject.create()

    private val foundAdapter = DeviceListAdapter(rowClickSubject)
    private val pairedAdapter = DeviceListAdapter(rowClickSubject)
    private var mBluetoothAdapter: BluetoothAdapter? = null

    private val PERMISSION_REQUEST_COARSE_LOCATION = 0
    private val REQUEST_ENABLE_BT = 1

    private val subscriptions: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_REQUEST_COARSE_LOCATION)
        }
        view_recycler_found.adapter = foundAdapter
        view_recycler_found.layoutManager = LinearLayoutManager(this)
        view_recycler_found.itemAnimator = DefaultItemAnimator()
        view_recycler_found.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        view_recycler_paired.adapter = pairedAdapter
        view_recycler_paired.layoutManager = LinearLayoutManager(this)
        view_recycler_paired.itemAnimator = DefaultItemAnimator()
        view_recycler_paired.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))

        subscriptions.addAll(
                RxView.clicks(view_button_search)
                        .filter { mBluetoothAdapter != null && !mBluetoothAdapter!!.isDiscovering}
                        .subscribe {
                            view_button_search.isEnabled = false
                            foundAdapter.clear()

                            mBluetoothAdapter!!.startDiscovery()

                            finishScanSubject.onNext(Any())
                            enableButtonSubject.onNext(false)
                            showProgressSubject.onNext(true)
                        },
                enableButtonSubject
                        .subscribe(RxView.enabled(view_button_search)),
                showProgressSubject
                        .subscribe(RxView.visibility(view_progress_bar, View.GONE)),
                discoveredSubject
                        .subscribe {
                            foundAdapter.addItems(it)
                        },
                rowClickSubject
                        .subscribe{
                            val intent = Intent(this, DaftActivity::class.java)
                            intent.putExtra(INTENT_EXTRA_BT_DEVICE,it)
                            startActivity(intent)
                        },
                finishScanSubject
                        .subscribeOn(Schedulers.io())
                        .delay(5, TimeUnit.SECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            mBluetoothAdapter!!.cancelDiscovery()
                            enableButtonSubject.onNext(true)
                            showProgressSubject.onNext(false)
                        }
        )

        initRxBluetooth()
    }


    private fun initRxBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (mBluetoothAdapter == null) {
            // handle the lack of bluetooth support
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage(R.string.bluetooth_not_supported)
                    .setCancelable(false)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.ok, { _, _ ->
                        android.os.Process.killProcess(android.os.Process.myPid())
                        System.exit(1)
                    })
        }

        if (!mBluetoothAdapter!!.isEnabled) {
            // Bluetooth is not enabled
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            enableButtonSubject.onNext(true)

            mBluetoothAdapter?.bondedDevices
                    ?.toList()
                    ?.forEach {
                        pairedAdapter.addItems(it)
                    }

            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(receiver, filter)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                discoveredSubject.onNext(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            initRxBluetooth()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            @NonNull permissions: Array<String>,
                                            @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_COARSE_LOCATION) {
            permissions
                    .filter { android.Manifest.permission.ACCESS_FINE_LOCATION == it }
                    .forEach { initRxBluetooth() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        mBluetoothAdapter?.cancelDiscovery()
        subscriptions.dispose()
    }
}
