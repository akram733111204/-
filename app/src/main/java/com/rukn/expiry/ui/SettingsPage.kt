package com.rukn.expiry.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rukn.core.AppData
import com.rukn.expiry.AppViewModel
import kotlinx.coroutines.launch

@Composable fun SettingsPage(vm:AppViewModel,data:AppData,onTasks:()->Unit,onCatalog:()->Unit){
    val scope=rememberCoroutineScope()
    var proxy by remember(data.settings["officialProxy"]){mutableStateOf(data.settings["officialProxy"].orEmpty())}
    var community by remember(data.settings["communityFallback"]){mutableStateOf(data.settings["communityFallback"]=="true")}
    var customThresholds by remember(data.settings["alertDays"]){mutableStateOf(data.settings["alertDays"]?:"7,30,60,90,120")}
    var notice by remember{mutableStateOf("")}
    var importConfirm by remember{mutableStateOf(false)}
    var pendingUri by remember{mutableStateOf<android.net.Uri?>(null)}
    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){uri->
        if(uri!=null)scope.launch{runCatching{vm.exportWorkbook(uri)}.onSuccess{notice="تم إنشاء نسخة Excel احتياطية بنجاح"}.onFailure{notice="فشل النسخ: ${it.message}"}}
    }
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){pendingUri=uri;importConfirm=true}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
        SectionHeader("إعدادات التطبيق","حماية البيانات والاتصال بمعلومات المنتجات")
        ElevatedCard{
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("الربط ببيانات المنتجات الرسمية",fontWeight=FontWeight.Bold)
                Text("ضع رابط خادم HTTPS يملكه فريقك ويستخدم تصريح الوصول الرسمي. لا تضع مفتاح API داخل التطبيق.",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(proxy,{proxy=it},label={Text("رابط خدمة المنتج (HTTPS)")},placeholder={Text("https://api.example.com")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedButton(onClick={scope.launch{
                    val v=proxy.trim().trimEnd('/')
                    if(v.isNotEmpty() && !v.startsWith("https://"))notice="رابط الخدمة يجب أن يستخدم HTTPS"
                    else runCatching{vm.saveSetting("officialProxy",v)}.onSuccess{notice="حُفظ رابط الخدمة"}.onFailure{notice=it.message.orEmpty()}
                }}){Icon(Icons.Outlined.Save,null);Text("حفظ رابط المصدر")}
                Row{
                    Switch(checked=community,onCheckedChange={value->community=value;scope.launch{vm.saveSetting("communityFallback",value.toString())}})
                    Column(Modifier.padding(start=12.dp)){
                        Text("السماح بالبيانات المجتمعية كخيار ثانوي",style=MaterialTheme.typography.bodyMedium)
                        Text("Open Food Facts غير حكومي؛ معلوماته تحتاج تحققًا يدويًا.",style=MaterialTheme.typography.bodySmall)
                    }
                }
                Text("عند غياب الربط أو تعطل الإنترنت تظل إضافة المنتجات يدويًا متاحة.",style=MaterialTheme.typography.labelSmall)
            }
        }
        ElevatedCard{
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("فترات مراقبة الصلاحية",fontWeight=FontWeight.Bold)
                Text("حدد الفترات بالأيام، مثل 7,15,30,60,120 — يرتبها التطبيق ويمنع القيم غير الصحيحة.",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(customThresholds,{customThresholds=it},modifier=Modifier.fillMaxWidth(),label={Text("فترات التنبيه بالأيام")})
                OutlinedButton(onClick={
                    val values=customThresholds.split(',').mapNotNull{it.trim().toIntOrNull()}.filter{it in 1..730}.distinct().sorted()
                    if(values.isEmpty()||values.size>8)notice="اختر من 1 إلى 8 فترات صحيحة (1–730 يومًا)"
                    else scope.launch{runCatching{vm.saveSetting("alertDays",values.joinToString(","))}.onSuccess{notice="تم حفظ فترات التنبيه"}.onFailure{notice=it.message.orEmpty()}}
                }){Text("حفظ الفترات")}
            }
        }
        ElevatedCard{
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("إدارة كتالوج المنتجات والباركود",fontWeight=FontWeight.Bold)
                Text("قالب Excel فارغ، واستيراد بيانات المنتجات مع معاينة ودمج آمن، وتصدير الكتالوج المرجعي.",style=MaterialTheme.typography.bodySmall)
                Button(onClick=onCatalog,modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.Inventory2,null);Spacer(Modifier.width(6.dp));Text("فتح إدارة كتالوج المنتجات")
                }
                Text("الكتالوج مستقل عن الاستعادة الكاملة لتواريخ الصلاحية.",style=MaterialTheme.typography.labelSmall)
            }
        }
        ElevatedCard{
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("ملفات Excel والنسخ الاحتياطي",fontWeight=FontWeight.Bold)
                Text("الملف الداخلي XLSX هو المرجع الدائم. أنشئ نسخة خارج الهاتف دوريًا، خاصة قبل تغيير الجهاز.",style=MaterialTheme.typography.bodySmall)
                Button(onClick={exportLauncher.launch("Rukn-Expiry-backup.xlsx")},modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.CloudDownload,null);Text("تصدير نسخة Excel كاملة")
                }
                OutlinedButton(onClick={importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/zip","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.Restore,null);Text("استعادة نسخة Excel محفوظة")
                }
                Text("تحذير: الاستعادة تستبدل جميع البيانات الموجودة حاليًا ولا تدمج الملفات. احفظ نسخة أولًا.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            }
        }
        ElevatedCard{
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text("العمليات والمتابعة",fontWeight=FontWeight.Bold)
                OutlinedButton(onClick=onTasks,modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.Assignment,null);Text("فتح مهام الفحص")
                }
                Text("عدد الأحداث المحفوظة في سجل العمليات: ${data.audit.size}",style=MaterialTheme.typography.bodySmall)
                Text("فترة التشغيل محفوظة وفق تاريخ الهاتف؛ تأكد من ضبط التاريخ والوقت الصحيحين.",style=MaterialTheme.typography.bodySmall)
            }
        }
        if(notice.isNotBlank()) Text(notice,color=MaterialTheme.colorScheme.primary)
        Text("الركن للصلاحية • النسخة التطويرية 0.2.0",style=MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
    }
    if(importConfirm) AlertDialog(onDismissRequest={importConfirm=false;pendingUri=null},
        title={Text("تأكيد استعادة Excel")},
        text={Text("ستُستبدل جميع المنتجات والتشغيلات والملاحظات والمهام ببيانات الملف المحدد. لا يمكن التراجع دون نسخة احتياطية. هل تريد المتابعة؟")},
        confirmButton={TextButton(onClick={
            val uri=pendingUri;importConfirm=false;pendingUri=null
            if(uri!=null)scope.launch{runCatching{vm.importWorkbook(uri)}.onSuccess{notice="تمت الاستعادة بنجاح"}.onFailure{notice="فشلت الاستعادة ولم تُستبدل البيانات: ${it.message}"}}
        }){Text("نعم، استعادة البيانات")}},
        dismissButton={TextButton(onClick={importConfirm=false;pendingUri=null}){Text("إلغاء")}})
}
