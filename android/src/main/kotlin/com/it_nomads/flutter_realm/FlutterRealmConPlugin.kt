package com.it_nomads.flutter_realm

import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.realm.Realm
import io.realm.SyncConfiguration
import io.realm.SyncUser
import java.util.*
import kotlin.collections.ArrayList

/** FlutterRealmConPlugin */
public class FlutterRealmConPlugin: FlutterPlugin, MethodCallHandler {
  private lateinit var channel : MethodChannel
  private val realms = HashMap<String, FlutterRealm>()
  private val handlers: List<MethodSubHandler> = ArrayList()

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    Realm.init(flutterPluginBinding.applicationContext)

    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "plugins.it_nomads.com/flutter_realm")
    channel.setMethodCallHandler(this);
  }

  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      Realm.init(registrar.context())

      val channel = MethodChannel(registrar.messenger(), "plugins.it_nomads.com/flutter_realm")
      channel.setMethodCallHandler(FlutterRealmConPlugin())
    }
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    try {
      val arguments = call.arguments as Map<*, *>
      for (handler in handlers) {
        if (handler.onMethodCall(call, result)) {
          return
        }
      }
      when (call.method) {
        "initialize" -> {
          onInitialize(result, arguments)
        }
        "reset" -> onReset(result)
        "asyncOpenWithConfiguration" -> onAsyncOpenWithConfiguration(arguments, result)
        "syncOpenWithConfiguration" -> onSyncOpenWithConfiguration(arguments, result)
        else -> {
          val realmId = arguments["realmId"] as String?
          val flutterRealm = realms[realmId]
          if (flutterRealm == null) {
            val message = "Method " + call.method + ":" + arguments.toString()
            result.error("Realm not found", message, null)
            return
          }
          flutterRealm.onMethodCall(call, result)
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      result.error(e.message, e.message, e.stackTrace.toString())
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  private fun onInitialize(result: Result, arguments: Map<*, *>) {
    val realmId = arguments["realmId"] as String?
    val flutterRealm = FlutterRealm(channel, realmId!!, arguments)
    realms[realmId] = flutterRealm
    result.success(null)
  }

  private fun onReset(result: Result) {
    for (realm in realms.values) {
      realm.reset()
    }
    realms.clear()
    result.success(null)
  }

  private fun onAsyncOpenWithConfiguration(arguments: Map<*, *>, result: Result) {
    val realmId = arguments["realmId"] as String?
    val configuration: SyncConfiguration = getSyncConfiguration(arguments)
    Realm.getInstanceAsync(configuration, object : Realm.Callback() {
      override fun onSuccess(realm: Realm) {
        val flutterRealm = FlutterRealm(channel, realmId!!, realm)
        realms[realmId] = flutterRealm
        result.success(null)
      }

      override fun onError(exception: Throwable) {
        result.error(exception.localizedMessage, exception.message, exception)
      }
    })
  }

  private fun onSyncOpenWithConfiguration(arguments: Map<*, *>, result: Result) {
    val realmId = arguments["realmId"] as String?
    val configuration: SyncConfiguration = getSyncConfiguration(arguments)
    val flutterRealm = FlutterRealm(channel, realmId!!, configuration)
    realms[realmId] = flutterRealm
    result.success(null)
  }

  private fun getSyncConfiguration(arguments: Map<*, *>): SyncConfiguration {
    val syncServerURL = arguments["syncServerURL"] as String?
    val fullSynchronization = arguments["fullSynchronization"] as Boolean
    val builder = SyncUser.current().createConfiguration(syncServerURL)
    if (fullSynchronization) {
      builder.fullSynchronization()
    }
    return builder.build()
  }
}
