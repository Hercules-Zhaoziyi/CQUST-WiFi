package cn.ziyis.cqustwifi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.*
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*
import java.util.concurrent.atomic.AtomicBoolean

data class ScannerUiState(val camera:Boolean=false,val torch:Boolean=false,val error:String="",val animations:Boolean=true)
enum class ScannerAction { BACK, FLASH, GALLERY, MANUAL, GRANT }

class ProxyScannerActivity:ComponentActivity(){
    private var state by mutableStateOf(ScannerUiState())
    private var camera by mutableStateOf<BarcodeView?>(null)
    private val delivered=AtomicBoolean(false)
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)openCamera()}
    private val picture=registerForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)readPicture(uri)}

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        androidx.core.view.WindowInsetsControllerCompat(window,window.decorView).apply{
            isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false
        }
        state=state.copy(animations=Repository.state.value.prefs.animations)
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)openCamera()
        setContent{val theme=remember{ThemeController(ColorSchemeMode.Dark)}
            MiuixTheme(controller=theme){ScannerScreen(state,camera,::act)}
        }
    }
    private fun openCamera(){
        if(camera==null)camera=BarcodeView(this).apply{
            decoderFactory=DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            decodeContinuous(object:BarcodeCallback{
                override fun barcodeResult(result:BarcodeResult?){result?.text?.let(::accept)}
                override fun possibleResultPoints(resultPoints:MutableList<ResultPoint>?){ }
            })
        }
        state=state.copy(camera=true);camera?.resume()
    }
    private fun act(action:ScannerAction){when(action){
        ScannerAction.BACK->finish()
        ScannerAction.MANUAL->{setResult(Activity.RESULT_CANCELED,Intent().putExtra("manual",true));finish()}
        ScannerAction.GALLERY->picture.launch("image/*")
        ScannerAction.GRANT->permission.launch(Manifest.permission.CAMERA)
        ScannerAction.FLASH->{state=state.copy(torch=!state.torch);camera?.setTorch(state.torch)}
    }}
    private fun accept(raw:String){
        if(delivered.get())return
        if(runCatching{ProxyQr.parse(raw)}.isFailure){state=state.copy(error=getString(R.string.proxy_scan_invalid));return}
        if(delivered.compareAndSet(false,true)){camera?.pause();setResult(Activity.RESULT_OK,Intent().putExtra("proxy_qr",raw));finish()}
    }
    private fun readPicture(uri:Uri){lifecycleScope.launch{
        val raw=withContext(Dispatchers.IO){runCatching{decodePicture(uri)}.getOrNull()}
        if(raw==null)state=state.copy(error=getString(R.string.proxy_scan_invalid_picture))else accept(raw)
    }}
    private fun decodePicture(uri:Uri):String{
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth in 1..50000&&bounds.outHeight in 1..50000)
        var sample=1;while(bounds.outWidth/sample>1800||bounds.outHeight/sample>1800)sample*=2
        val options=BitmapFactory.Options().apply{inSampleSize=sample}
        val bitmap=contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,options)}?:error("IMAGE_DECODE_FAILED")
        try{
            val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val source=RGBLuminanceSource(bitmap.width,bitmap.height,pixels)
            return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)),mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),DecodeHintType.TRY_HARDER to true)).text
        }finally{bitmap.recycle()}
    }
    override fun onResume(){super.onResume();if(state.camera)camera?.resume()}
    override fun onPause(){camera?.pause();super.onPause()}
    override fun onDestroy(){camera?.pause();camera=null;super.onDestroy()}
}

@Composable internal fun ScannerScreen(state:ScannerUiState,camera:BarcodeView?,onAction:(ScannerAction)->Unit){
    val transition=rememberInfiniteTransition(label="scanner")
    val scanY by transition.animateFloat(0f,1f,infiniteRepeatable(tween(2200, easing=LinearEasing),RepeatMode.Reverse),label="scan-line")
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xff11151c))){
        val frameSide=minOf(maxWidth*.72f,280.dp)
        if(state.camera&&camera!=null)AndroidView(factory={camera},modifier=Modifier.fillMaxSize())
        if(state.camera)ScannerMask(if(state.animations)scanY else .5f)
        if(state.camera)Text(state.error.ifBlank{stringResource(R.string.proxy_scan_hint)},
            Modifier.fillMaxWidth().padding(horizontal=26.dp).offset(y=maxHeight*.44f+frameSide/2+22.dp),
            textAlign=TextAlign.Center,color=if(state.error.isBlank())Color.White else Color(0xffffc29b),fontSize=14.sp,lineHeight=22.sp)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=24.dp)){
            Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={onAction(ScannerAction.BACK)}){Icon(Icons.Outlined.ArrowBack,stringResource(R.string.proxy_scan_back),tint=Color.White)}
                Text(stringResource(R.string.proxy_scan_title),Modifier.weight(1f),color=Color.White,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                IconButton(onClick={onAction(ScannerAction.FLASH)},enabled=state.camera){Icon(if(state.torch)Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,stringResource(R.string.proxy_scan_flash),tint=if(state.camera)Color.White else Color.White.copy(alpha=.3f))}
            }
            Text(stringResource(R.string.proxy_scan_subtitle),Modifier.fillMaxWidth().padding(top=10.dp),textAlign=TextAlign.Center,color=Color(0xffbdc7d4),fontSize=14.sp)
            Spacer(Modifier.weight(1f))
            if(!state.camera)Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.07f),RoundedCornerShape(26.dp)).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
                Icon(Icons.Outlined.QrCodeScanner,null,Modifier.size(52.dp),tint=Color(0xff98b4ff))
                Text(stringResource(R.string.proxy_scan_permission_title),color=Color.White,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                Text(stringResource(R.string.proxy_scan_permission_body),color=Color(0xffbdc7d4),fontSize=14.sp,lineHeight=23.sp,textAlign=TextAlign.Center)
                Button(onClick={onAction(ScannerAction.GRANT)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColorsPrimary()){Text(stringResource(R.string.proxy_scan_permission_button))}
            }
            Spacer(Modifier.weight(1f))
            if(!state.camera&&state.error.isNotEmpty())Text(state.error,Modifier.fillMaxWidth(),textAlign=TextAlign.Center,
                color=if(state.error.isBlank())Color.White else Color(0xffffc29b),fontSize=14.sp,lineHeight=22.sp)
            Row(Modifier.fillMaxWidth().padding(top=28.dp,bottom=20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Button(onClick={onAction(ScannerAction.GALLERY)},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(color=Color.White.copy(alpha=.12f),contentColor=Color.White)){
                    Icon(Icons.Outlined.PhotoLibrary,null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp));Text(stringResource(R.string.proxy_scan_gallery),fontSize=13.sp)
                }
                Button(onClick={onAction(ScannerAction.MANUAL)},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(color=Color.White.copy(alpha=.12f),contentColor=Color.White)){
                    Icon(Icons.Outlined.Edit,null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp));Text(stringResource(R.string.proxy_scan_manual),fontSize=13.sp)
                }
            }
            Text(stringResource(R.string.proxy_scan_private),Modifier.fillMaxWidth().padding(bottom=20.dp),textAlign=TextAlign.Center,color=Color(0xff8b98aa),fontSize=11.sp)
        }
    }
}

@Composable private fun ScannerMask(progress:Float){
    Canvas(Modifier.fillMaxSize()){
        val side=minOf(size.width*.72f,280.dp.toPx());val left=(size.width-side)/2;val top=size.height*.44f-side/2
        val rect=Rect(left,top,left+side,top+side)
        val shade=Path().apply{fillType=PathFillType.EvenOdd;addRect(Rect(Offset.Zero,size));addRoundRect(RoundRect(rect,CornerRadius(24.dp.toPx())))}
        drawPath(shade,Color.Black.copy(alpha=.54f))
        drawRoundRect(Color.White.copy(alpha=.8f),rect.topLeft,rect.size,CornerRadius(24.dp.toPx()),style=Stroke(2.dp.toPx()))
        val y=top+18.dp.toPx()+(side-36.dp.toPx())*progress
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent,Color(0xff89b4ff),Color.Transparent)),Offset(left+12.dp.toPx(),y),Offset(left+side-12.dp.toPx(),y),3.dp.toPx())
    }
}
