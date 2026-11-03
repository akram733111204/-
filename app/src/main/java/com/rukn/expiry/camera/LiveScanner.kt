package com.rukn.expiry.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rukn.core.DateTextParser
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Live camera frame analysis; never asks the user to capture a still photograph. */
@Composable
fun ScannerDialog(mode:String,onDismiss:()->Unit,onBarcode:(String)->Unit,onDates:(DateTextParser.Result)->Unit){
    val ctx=LocalContext.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed=it}
    LaunchedEffect(Unit){if(!allowed) permissions.launch(Manifest.permission.CAMERA)}
    var rawText by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss, confirmButton={TextButton(onClick=onDismiss){Text("إغلاق")}},
        title={Text(if(mode=="barcode")"مسح الباركود المباشر" else "مسح تواريخ العبوة المباشر")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(allowed) {
                Box(Modifier.fillMaxWidth().height(320.dp)) {
                    LiveScanner(mode=mode,modifier=Modifier.fillMaxSize(),onBarcode=onBarcode,onDates=onDates,onRawText={rawText=it})
                    Box(Modifier.align(Alignment.Center).fillMaxWidth(0.83f).height(150.dp)
                        .background(androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp)))
                }
                Text(if(mode=="barcode")"وجّه الرمز داخل الكاميرا؛ يتم التقاطه دون الضغط على زر تصوير."
                    else "حرّك الكاميرا فوق تاريخ الإنتاج والانتهاء. ستُملأ الحقول، ويجب مراجعتها قبل الحفظ.",style=MaterialTheme.typography.bodySmall)
                if(mode=="date" && rawText.isNotBlank()) Text("آخر نص مقروء: ${rawText.take(130)}",style=MaterialTheme.typography.labelSmall)
            } else {
                Text("يلزم السماح للكاميرا. يمكنك أيضًا إغلاق الماسح وإدخال البيانات يدويًا.")
                Button(onClick={permissions.launch(Manifest.permission.CAMERA)}){Text("طلب إذن الكاميرا")}
            }
        }},modifier=Modifier.fillMaxWidth())
}

@Composable
private fun LiveScanner(mode:String,modifier:Modifier=Modifier,onBarcode:(String)->Unit,onDates:(DateTextParser.Result)->Unit,onRawText:(String)->Unit){
    val context=LocalContext.current
    val lifecycleOwner=LocalLifecycleOwner.current
    val previewView=remember {PreviewView(context).apply{implementationMode=PreviewView.ImplementationMode.COMPATIBLE}}
    val onCodeLatest=rememberUpdatedState(onBarcode)
    val onDatesLatest=rememberUpdatedState(onDates)
    val onTextLatest=rememberUpdatedState(onRawText)
    val executor=remember {Executors.newSingleThreadExecutor()}
    var torch by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(0f) }
    val cameraRef=remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    LaunchedEffect(torch,cameraRef.value){cameraRef.value?.cameraControl?.enableTorch(torch)}
    LaunchedEffect(zoom,cameraRef.value){cameraRef.value?.cameraControl?.setLinearZoom(zoom)}
    DisposableEffect(mode,lifecycleOwner){
        val providerFuture=ProcessCameraProvider.getInstance(context)
        val disposed=AtomicBoolean(false)
        val busy=AtomicBoolean(false)
        val emitted=AtomicBoolean(false)
        val barcodeReader=if(mode=="barcode")BarcodeScanning.getClient() else null
        val textReader=if(mode=="date")TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) else null
        var lastScan=0L
        var lastSignature=""
        var stable=0
        val listener=Runnable{
            if(disposed.get())return@Runnable
            try{
                val provider=providerFuture.get()
                val preview=Preview.Builder().build().also{it.surfaceProvider=previewView.surfaceProvider}
                val analysis=ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor){ frame ->
                    val media=frame.image
                    if(media==null || emitted.get() || !busy.compareAndSet(false,true) || SystemClock.elapsedRealtime()-lastScan<260){
                        frame.close();return@setAnalyzer
                    }
                    lastScan=SystemClock.elapsedRealtime()
                    val input=InputImage.fromMediaImage(media,frame.imageInfo.rotationDegrees)
                    if(mode=="barcode"){
                        barcodeReader?.process(input)?.addOnSuccessListener{codes ->
                            codes.firstOrNull{!it.rawValue.isNullOrBlank()}?.rawValue?.let{if(emitted.compareAndSet(false,true))onCodeLatest.value(it)}
                        }?.addOnCompleteListener{busy.set(false);frame.close()}
                            ?:run{busy.set(false);frame.close()}
                    } else {
                        textReader?.process(input)?.addOnSuccessListener{result ->
                            if(result.text.isNotBlank())onTextLatest.value(result.text)
                            val parsed=DateTextParser.parse(result.text)
                            val sig="${parsed.production?.iso.orEmpty()}|${parsed.expiry?.iso.orEmpty()}"
                            if(sig!="|" && !(parsed.production?.ambiguous==true || parsed.expiry?.ambiguous==true)) {
                                if(sig==lastSignature)stable++ else {lastSignature=sig;stable=1}
                                if(stable==3 && emitted.compareAndSet(false,true))onDatesLatest.value(parsed)
                            }else{lastSignature="";stable=0}
                        }?.addOnCompleteListener{busy.set(false);frame.close()}
                            ?:run{busy.set(false);frame.close()}
                    }
                }
                provider.unbindAll()
                cameraRef.value=provider.bindToLifecycle(lifecycleOwner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)
            }catch(_:Exception){}
        }
        providerFuture.addListener(listener,ContextCompat.getMainExecutor(context))
        onDispose{
            disposed.set(true)
            if(providerFuture.isDone)runCatching{providerFuture.get().unbindAll()}
            barcodeReader?.close();textReader?.close();executor.shutdown()
        }
    }
    Box(modifier){
        AndroidView(factory={previewView},modifier=Modifier.fillMaxSize())
        FilledTonalIconButton(onClick={torch=!torch},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)){
            Icon(if(torch)Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,contentDescription="تشغيل الإضاءة")
        }
        Slider(value=zoom,onValueChange={zoom=it},modifier=Modifier.align(Alignment.BottomCenter).padding(horizontal=20.dp,vertical=8.dp))
    }
}
