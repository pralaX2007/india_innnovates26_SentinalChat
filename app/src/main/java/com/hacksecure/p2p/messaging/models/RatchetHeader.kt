package com.hacksecure.p2p.messaging.models


data class RatchetHeader(


    val dhPublicKey: ByteArray,


    val messageNumber: Int,


    val previousChainLength: Int
)