package com.rukn.core

import java.math.BigDecimal
import java.math.RoundingMode

object QuantityEngine {
    data class Summary(val cartons:String,val pieces:String,val included:Int,val excluded:Int,val complete:Boolean)
    private val cartonUnits=setOf("ctn","carton","cartons","كرتون","كراتين")
    private val pieceUnits=setOf("pcs","pc","piece","pieces","حبه","حبة","قطعه","قطعة","عبوه","عبوة","عدد")
    fun summarize(product:Product,batches:List<Batch>):Summary? {
        val active=batches.filter{it.status==BatchStatus.ACTIVE}
        if(active.isEmpty())return null
        val factor=product.unitsPerCarton.trim().toBigDecimalOrNull()?.takeIf{it>BigDecimal.ZERO}
        var pieces=BigDecimal.ZERO
        var included=0
        for(b in active){
            val raw=b.quantity.trim()
            // A lone comma before exactly three trailing digits might be a thousands separator;
            // do not silently reinterpret 1,000 as 1 piece.
            val ambiguousGrouping=raw.matches(Regex("\\d{1,3}(,\\d{3})+"))
            val amount=if(ambiguousGrouping || raw.contains(',') && raw.contains('.')) null
                else raw.replace(',','.').toBigDecimalOrNull()
            val unit=ArabicSearch.normalize(b.unit)
            if(amount==null || amount<BigDecimal.ZERO)continue
            val normalized=when {
                unit in pieceUnits->amount
                unit in cartonUnits && factor!=null->amount.multiply(factor)
                else->continue
            }
            pieces+=normalized;included++
        }
        if(included==0)return null
        val cartons=if(factor==null)BigDecimal.ZERO else pieces.divideToIntegralValue(factor)
        val rest=if(factor==null)pieces else pieces.remainder(factor)
        fun fmt(d:BigDecimal)=d.stripTrailingZeros().toPlainString()
        return Summary(if(factor!=null)fmt(cartons)else "—",fmt(rest),included,active.size-included,included==active.size)
    }
}
