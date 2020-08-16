package com.it_nomads.flutter_realm

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

interface MethodSubHandler {
    fun onMethodCall(call: MethodCall?, result: MethodChannel.Result?): Boolean
}