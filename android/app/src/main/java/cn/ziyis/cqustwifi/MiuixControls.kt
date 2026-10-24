package cn.ziyis.cqustwifi

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable internal fun ActionButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape=RoundedCornerShape(16.dp),content:@Composable RowScope.()->Unit){
    Button(onClick,modifier.clip(shape),enabled,colors=ButtonDefaults.buttonColorsPrimary(),content=content)
}

@Composable internal fun SecondaryButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape=RoundedCornerShape(16.dp),content:@Composable RowScope.()->Unit){
    Button(onClick,modifier.clip(shape),enabled,content=content)
}

@Composable internal fun QuietButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    content:@Composable RowScope.()->Unit){
    Button(onClick,modifier,enabled,minWidth=0.dp,minHeight=36.dp,insideMargin=PaddingValues(8.dp,6.dp),
        colors=ButtonDefaults.buttonColors(color=Color.Transparent,contentColor=MiuixTheme.colorScheme.primary),content=content)
}

@Composable internal fun ChoiceChip(selected:Boolean,onClick:()->Unit,label:@Composable ()->Unit,modifier:Modifier=Modifier){
    val color=MiuixTheme.colorScheme.primary
    Button(onClick,modifier,cornerRadius=12.dp,minWidth=0.dp,minHeight=38.dp,insideMargin=PaddingValues(13.dp,8.dp),
        colors=ButtonDefaults.buttonColors(color=if(selected)color.copy(alpha=.13f)else MiuixTheme.colorScheme.surfaceVariant,
            contentColor=if(selected)color else MiuixTheme.colorScheme.onSurfaceVariantSummary)){label()}
}

@Composable internal fun MiuixInput(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,
    label:(@Composable ()->Unit)?=null,placeholder:(@Composable ()->Unit)?=null,
    singleLine:Boolean=false,maxLines:Int=if(singleLine)1 else Int.MAX_VALUE,shape:Shape=RoundedCornerShape(16.dp),
    visualTransformation:VisualTransformation=VisualTransformation.None,
    keyboardOptions:KeyboardOptions=KeyboardOptions.Default,trailingIcon:(@Composable ()->Unit)?=null){
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        label?.let{Box(Modifier.padding(start=3.dp)){it()}}
        Box(Modifier.fillMaxWidth()){
            TextField(value,onValueChange,modifier.fillMaxWidth().clip(shape),singleLine=singleLine,maxLines=maxLines,
                keyboardOptions=keyboardOptions,visualTransformation=visualTransformation,trailingIcon=trailingIcon)
            if(value.isEmpty()&&placeholder!=null)Box(Modifier.align(Alignment.CenterStart).padding(horizontal=16.dp)){placeholder()}
        }
    }
}

@Composable internal fun MiuixPrompt(onDismissRequest:()->Unit,title:@Composable ()->Unit,
    text:(@Composable ()->Unit)?=null,confirmButton:@Composable ()->Unit,
    dismissButton:(@Composable ()->Unit)?=null,properties:DialogProperties=DialogProperties(),
    containerColor:Color=MiuixTheme.colorScheme.surfaceContainer){
    val activity=LocalContext.current as? Activity
    val secure=properties.securePolicy==SecureFlagPolicy.SecureOn
    var ready by remember{mutableStateOf(!secure)}
    DisposableEffect(activity,secure){
        val wasSecure=activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE)!=0
        if(secure)activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        ready=true
        onDispose{if(secure&&!wasSecure)activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
    }
    if(ready)WindowDialog(show=true,onDismissRequest=onDismissRequest,backgroundColor=containerColor){
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(17.dp)){
            title();text?.invoke()
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End),verticalAlignment=Alignment.CenterVertically){
                dismissButton?.invoke();confirmButton()
            }
        }
    }
}
