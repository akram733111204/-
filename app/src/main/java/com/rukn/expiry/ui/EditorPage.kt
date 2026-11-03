package com.rukn.expiry.ui

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
import com.rukn.core.*
import com.rukn.expiry.AppViewModel
import com.rukn.expiry.camera.ScannerDialog
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable private fun InputLine(value:String,onChange:(String)->Unit,label:String,placeholder:String="",modifier:Modifier=Modifier){
    OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},placeholder={Text(placeholder)},modifier=modifier.fillMaxWidth(),singleLine=true)
}
private fun validDate(text:String):Boolean =try{
    if(text.isBlank()) true else {if(text.length==7)YearMonth.parse(text)else LocalDate.parse(text);true}
}catch(_:Exception){false}
private fun comparableDate(text:String):LocalDate? =try{
    if(text.isBlank())null else if(text.length==7)YearMonth.parse(text).atEndOfMonth()else LocalDate.parse(text)
}catch(_:Exception){null}

@Composable fun EditorPage(vm:AppViewModel,data:AppData,productId:String,batchId:String,
    initialCatalog:CatalogProduct?=null,onSaved:(String)->Unit){
    val existing=data.products.firstOrNull{it.id==productId}
    val initial=existing?:initialCatalog?.forEditor()
    val existingBatch=data.batches.firstOrNull{it.id==batchId}
    var workingId by remember(productId,initialCatalog?.id){mutableStateOf(existing?.id?:Product().id)}
    var barcode by remember(productId){mutableStateOf(initial?.barcode.orEmpty())}
    var ar by remember(productId){mutableStateOf(initial?.nameAr.orEmpty())}
    var en by remember(productId){mutableStateOf(initial?.nameEn.orEmpty())}
    var brand by remember(productId){mutableStateOf(initial?.brand.orEmpty())}
    var size by remember(productId){mutableStateOf(initial?.size.orEmpty())}
    var unitsPerCarton by remember(productId){mutableStateOf(initial?.unitsPerCarton.orEmpty())}
    var category by remember(productId){mutableStateOf(initial?.category.orEmpty())}
    var aisle by remember(productId){mutableStateOf(initial?.aisle.orEmpty())}
    var shelf by remember(productId){mutableStateOf(initial?.shelf.orEmpty())}
    var productNotes by remember(productId){mutableStateOf(initial?.notes.orEmpty())}
    var source by remember(productId){mutableStateOf(initial?.source?:"manual")}
    var sourceUrl by remember(productId){mutableStateOf(initial?.sourceUrl.orEmpty())}
    var lot by remember(batchId){mutableStateOf(existingBatch?.lot.orEmpty())}
    var mfg by remember(batchId){mutableStateOf(existingBatch?.productionDate.orEmpty())}
    var exp by remember(batchId){mutableStateOf(existingBatch?.expiryDate.orEmpty())}
    var quantity by remember(batchId){mutableStateOf(existingBatch?.quantity.orEmpty())}
    var unit by remember(batchId){mutableStateOf(existingBatch?.unit.orEmpty())}
    var location by remember(batchId){mutableStateOf(existingBatch?.location.orEmpty())}
    var batchNotes by remember(batchId){mutableStateOf(existingBatch?.notes.orEmpty())}
    var expiryKind by remember(batchId){mutableStateOf(existingBatch?.dateKind?:DateKind.EXPIRY)}
    var status by remember(batchId){mutableStateOf(existingBatch?.status?:BatchStatus.ACTIVE)}
    var reviewed by remember(batchId){mutableStateOf(existingBatch?.verified?:false)}
    var scanMode by remember{mutableStateOf("")}
    var lookupBusy by remember{mutableStateOf(false)}
    var lookupMode by remember { mutableStateOf(LookupMode.parse(data.settings["lookupMode"])) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var feedback by remember{mutableStateOf("")}
    var saving by remember{mutableStateOf(false)}
    var allowDuplicate by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val dupe=remember(data.batches,data.products,barcode,workingId,lot,exp,location,batchId){
        val effectiveId=data.products.firstOrNull{BarcodeRules.normalize(it.barcode)==BarcodeRules.normalize(barcode) && barcode.isNotBlank()}?.id ?: workingId
        (lot.isNotBlank() || exp.isNotBlank()) && data.batches.any{it.id!=batchId && it.productId==effectiveId && it.lot==lot && it.expiryDate==exp && it.location==location}
    }
    fun applyLookup(code:String){
        if(code.isBlank()){
            feedback="لم يُقرأ باركود؛ يمكنك إدخال البيانات يدويًا دون حقول إجبارية"
            return
        }
        scope.launch {
            lookupBusy=true
            try {
                val response=vm.resolveBarcode(code,lookupMode)
                feedback=response.message
                response.product?.let { found ->
                    if(existing!=null && found.barcode.isNotBlank() &&
                        data.products.any{it.id!=existing.id && it.matchesBarcode(found.barcode)}) {
                        feedback="هذا الباركود مسجل لمنتج آخر. افتح السجل الموجود من شاشة البحث بدل دمجه دون مراجعة."
                    } else {
                        if(existing==null && found.source=="manual")workingId=found.id
                        // A referenced catalog entry is not an active expiry record; keep active identity untouched.
                        if(found.barcode.isNotBlank())barcode=found.barcode
                        if(ar.isBlank())ar=found.nameAr
                        if(en.isBlank())en=found.nameEn
                        if(brand.isBlank())brand=found.brand
                        if(size.isBlank())size=found.size
                        if(unitsPerCarton.isBlank())unitsPerCarton=found.unitsPerCarton
                        if(category.isBlank())category=found.category
                        if(aisle.isBlank())aisle=found.aisle
                        if(shelf.isBlank())shelf=found.shelf
                        if(productNotes.isBlank())productNotes=found.notes
                        if(source=="manual" && found.source!="manual")source=found.source
                        if(sourceUrl.isBlank())sourceUrl=found.sourceUrl
                        if(found.displayName=="منتج غير مسمى")feedback += " — يمكنك إدخال أي معلومات تعرفها يدويًا."
                    }
                }
            }catch(e:Exception){feedback="تعذر البحث: ${e.message}. يمكنك الإكمال يدويًا."}
            finally{lookupBusy=false}
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        SectionHeader(if(existing!=null)"تعديل بيانات الصنف" else "إضافة منتج جديد","كل حقول الإدخال اختيارية — تُنشأ هوية داخلية تلقائيًا")
        ElevatedCard{
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("01 • التعرف على المنتج",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text("مصدر التعرف على الباركود",style=MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(onClick={dropdownExpanded=true},modifier=Modifier.fillMaxWidth()){
                        Text(when(lookupMode){
                            LookupMode.AUTO->"ذكي تلقائي: محفوظ ← كتالوج ← إنترنت"
                            LookupMode.LOCAL->"محلي فقط: محفوظ + كتالوج Excel"
                            LookupMode.ONLINE->"مصادر الإنترنت المصرح بها فقط"
                            LookupMode.MANUAL->"إدخال يدوي مباشرة"
                        },modifier=Modifier.weight(1f))
                        Icon(Icons.Outlined.ArrowDropDown,null)
                    }
                    DropdownMenu(expanded=dropdownExpanded,onDismissRequest={dropdownExpanded=false}){
                        listOf(
                            LookupMode.AUTO to "البحث الذكي التلقائي",
                            LookupMode.LOCAL to "المنتجات والكتالوج المحلي فقط",
                            LookupMode.ONLINE to "المصادر الخارجية فقط",
                            LookupMode.MANUAL to "الإدخال اليدوي مباشرة"
                        ).forEach{(mode,title)->
                            DropdownMenuItem(text={Text(title)},onClick={
                                dropdownExpanded=false;lookupMode=mode
                                scope.launch{runCatching{vm.saveSetting("lookupMode",mode.name)}}
                            })
                        }
                    }
                }
                Text("عند فشل التعرف أو عدم وجود إنترنت، تبقى جميع حقول الإدخال اليدوي متاحة فورًا. يُحفظ الباركود المقروء تلقائيًا.",style=MaterialTheme.typography.bodySmall)
                Button(onClick={scanMode="barcode"},modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.QrCodeScanner,null);Spacer(Modifier.width(8.dp));Text("مسح الباركود مباشرة بالكاميرا")
                }
                InputLine(barcode,{barcode=it},"الباركود","يمكن إدخاله يدويًا أو متابعة العملية بدونه")
                OutlinedButton(onClick={applyLookup(barcode)},enabled=!lookupBusy && barcode.isNotBlank()){
                    if(lookupBusy) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                    else Icon(Icons.Outlined.Search,null)
                    Spacer(Modifier.width(6.dp));Text("البحث عن المنتج الآن")
                }
                if(feedback.isNotBlank()) Text(feedback,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                InputLine(ar,{ar=it},"الاسم العربي")
                InputLine(en,{en=it},"الاسم الإنجليزي")
                InputLine(brand,{brand=it},"العلامة التجارية")
                InputLine(size,{size=it},"الحجم أو الوزن أو التعبئة")
                InputLine(unitsPerCarton,{unitsPerCarton=it},"عدد الحبات في الكرتون (اختياري)","مثال: 24")
                InputLine(category,{category=it},"القسم")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    InputLine(aisle,{aisle=it},"الممر",modifier=Modifier.weight(1f))
                    InputLine(shelf,{shelf=it},"رقم الرف",modifier=Modifier.weight(1f))
                }
                OutlinedTextField(productNotes,{productNotes=it},label={Text("ملاحظات المنتج")},modifier=Modifier.fillMaxWidth(),minLines=2)
            }
        }
        ElevatedCard{
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("02 • بيانات التشغيلة والصلاحية",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text("يمكنك حفظ المنتج دون تواريخ، ثم استكمالها لاحقًا.",style=MaterialTheme.typography.bodySmall)
                Button(onClick={scanMode="date"},modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.DocumentScanner,null);Spacer(Modifier.width(8.dp));Text("مسح تواريخ العبوة بالفيديو المباشر")
                }
                InputLine(mfg,{mfg=it;reviewed=false},"تاريخ الإنتاج","2026-11-03 أو 2026-11")
                InputLine(exp,{exp=it;reviewed=false},"تاريخ الانتهاء / الأفضل قبل","2027-05-25 أو 2027-05")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    FilterChip(expiryKind==DateKind.EXPIRY,onClick={expiryKind=DateKind.EXPIRY},label={Text("انتهاء")})
                    FilterChip(expiryKind==DateKind.BEST_BEFORE,onClick={expiryKind=DateKind.BEST_BEFORE},label={Text("الأفضل قبل")})
                }
                InputLine(lot,{lot=it},"رقم التشغيلة LOT")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    InputLine(quantity,{quantity=it},"الكمية",modifier=Modifier.weight(1f))
                    InputLine(unit,{unit=it},"وحدتها", "قطعة، كرتون...",Modifier.weight(1f))
                }
                InputLine(location,{location=it},"مكان التشغيلة","الممر والرف أو المخزن")
                OutlinedTextField(batchNotes,{batchNotes=it},label={Text("ملاحظات التشغيلة")},modifier=Modifier.fillMaxWidth(),minLines=2)
                Text("حالة التشغيلة",style=MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    FilterChip(status==BatchStatus.ACTIVE,onClick={status=BatchStatus.ACTIVE},label={Text("نشطة")})
                    FilterChip(status==BatchStatus.QUARANTINED,onClick={status=BatchStatus.QUARANTINED},label={Text("معزولة")})
                    FilterChip(status==BatchStatus.REMOVED,onClick={status=BatchStatus.REMOVED},label={Text("مسحوبة")})
                    FilterChip(status==BatchStatus.SOLD_OUT,onClick={status=BatchStatus.SOLD_OUT},label={Text("نفدت")})
                }
                Row{
                    Checkbox(checked=reviewed,onCheckedChange={reviewed=it})
                    Text("راجعت التاريخين بنفسي وقارنتهما بما هو مطبوع على العبوة",modifier=Modifier.padding(top=10.dp),style=MaterialTheme.typography.bodySmall)
                }
                if(dupe){
                    Text("تنبيه: توجد تشغيلة بنفس رقم LOT وتاريخ الانتهاء والموقع. تحقق من احتمال التكرار.",color=MaterialTheme.colorScheme.error)
                    Row{Checkbox(checked=allowDuplicate,onCheckedChange={allowDuplicate=it});Text("أؤكد إضافة هذا السجل رغم التشابه",modifier=Modifier.padding(top=11.dp))}
                }
            }
        }
        if(feedback.isNotBlank())Text(feedback,style=MaterialTheme.typography.bodySmall)
        Button(onClick={
            when {
                !validDate(mfg)||!validDate(exp)->feedback="صيغة التاريخ غير صحيحة. استخدم YYYY-MM-DD أو YYYY-MM."
                mfg.isNotBlank() && exp.isNotBlank() && comparableDate(mfg)!!>comparableDate(exp)!!->feedback="تاريخ الإنتاج أحدث من تاريخ الانتهاء؛ راجع المدخلات."
                (mfg.isNotBlank()||exp.isNotBlank()) && !reviewed->feedback="يجب مراجعة التواريخ المطبوعة وتأكيدها قبل حفظها."
                dupe && !allowDuplicate->feedback="أكد التعامل مع التشغيلة المشابهة قبل المتابعة."
                else->{
                    saving=true
                    scope.launch {
                        try{
                            val p=Product(id=workingId,barcode=barcode.trim(),nameAr=ar.trim(),nameEn=en.trim(),
                                brand=brand.trim(),size=size.trim(),unitsPerCarton=unitsPerCarton.trim(),category=category.trim(),aisle=aisle.trim(),shelf=shelf.trim(),
                                notes=productNotes,source=source,sourceUrl=sourceUrl,
                                createdAt=existing?.createdAt?:java.time.Instant.now().toString())
                            val hasBatch=existingBatch!=null || listOf(lot,mfg,exp,quantity,location,batchNotes).any{it.isNotBlank()}
                            val b=if(!hasBatch)null else Batch(id=existingBatch?.id?:java.util.UUID.randomUUID().toString(),productId=workingId,
                                lot=lot,productionDate=mfg,expiryDate=exp,
                                precision=if(exp.length==7)DatePrecision.MONTH else if(exp.length==10)DatePrecision.DAY else DatePrecision.UNKNOWN,
                                dateKind=expiryKind,quantity=quantity,unit=unit,location=location,notes=batchNotes,
                                status=status,verified=reviewed,lastCheckedAt=if(reviewed)java.time.Instant.now().toString() else existingBatch?.lastCheckedAt.orEmpty(),
                                createdAt=existingBatch?.createdAt?:java.time.Instant.now().toString())
                            val id=vm.saveProduct(p,b,allowDuplicateBatch=allowDuplicate)
                            onSaved(id)
                        }catch(e:Exception){feedback="فشل الحفظ: ${e.message}"}
                        finally{saving=false}
                    }
                }
            }
        },modifier=Modifier.fillMaxWidth(),enabled=!saving){
            Icon(Icons.Outlined.Save,null);Spacer(Modifier.width(8.dp));Text(if(saving)"جار حفظ البيانات..." else "حفظ المنتج والتشغيلة")
        }
        Spacer(Modifier.height(16.dp))
    }
    if(scanMode.isNotBlank())ScannerDialog(mode=scanMode,onDismiss={scanMode=""},onBarcode={code->
        val gs=Gs1Parser.parse(code)
        val lookupCode=gs?.gtin?:code
        barcode=lookupCode
        gs?.let{ if(it.lot.isNotBlank())lot=it.lot
            if(it.production.isNotBlank())mfg=it.production
            if(it.expiry.isNotBlank()){exp=it.expiry;expiryKind=it.kind}
            reviewed=false
        }
        scanMode=""
        applyLookup(lookupCode)
    },onDates={result->
        result.production?.let{mfg=it.iso}
        result.expiry?.let{exp=it.iso;expiryKind=if(it.label=="best_before")DateKind.BEST_BEFORE else DateKind.EXPIRY}
        if(result.lot.isNotBlank())lot=result.lot
        reviewed=false
        feedback=if(result.other.isNotEmpty())"هناك تواريخ غير مصنفة؛ راجع العبوة وتأكد قبل الحفظ." else "تم ملء التواريخ المرصودة. راجعها وقارنها بالعبوة ثم أكد."
        scanMode=""
    })
}
