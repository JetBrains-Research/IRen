package org.jetbrains.iren.contributors
//
//import io.kinference.ndarray.Strides
//import io.kinference.ndarray.arrays.LongNDArray
//import io.kinference.ndarray.arrays.MutableFloatNDArray
//import io.kinference.ndarray.arrays.tiled.LongTiledArray
//import io.kinference.ndarray.toIntArray
//import io.kinference.ndarray.toLongArray
//import org.jetbrains.iren.models.KInferenceModel
//import org.jetbrains.iren.search.logSoftmax
//
//class DOBFContributorKInference : DOBFContributor() {
//    val model = KInferenceModel(encoderPath, decoderPath)
//
//    override fun predictGreedy(idxs: List<Int>): Pair<List<Int>, Double> {
//        val (src_enc, src_len) = model.encode(idxs)
//
//        val eosIdx = vocab.toIndex("</s>")
//        val toDecList = mutableListOf(eosIdx)
//
////        Problem: it keeps generating VAR_i token
//        var logProb = .0
//        for (i in 0..5) {
//            val toDec = LongTiledArray(arrayOf(toDecList.toIntArray().toLongArray()))
//            val decIds = LongNDArray(toDec, Strides(intArrayOf(1, toDec.size)))  // Shape: [B, T]
//            val out = model.decode(decIds, src_enc, src_len)
//            val idx: Int = out.argmax(1).array[0]
//            if (idx == eosIdx) break
//            logProb += logSoftmax(arrayOf((out.row(0) as MutableFloatNDArray).array.toArray()))[0][idx]
//            toDecList.add(idx)
//        }
//        return toDecList to logProb
//    }
//}