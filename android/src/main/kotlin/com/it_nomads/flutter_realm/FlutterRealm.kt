package com.it_nomads.flutter_realm

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.realm.*
import java.util.*


class FlutterRealm {
    private val realmId: String
    private var realm: DynamicRealm
    private val subscriptions = HashMap<String?, RealmResults<*>?>()
    private val channel: MethodChannel

    constructor(channel: MethodChannel, realmId: String, arguments: Map<*, *>) {
        this.channel = channel
        this.realmId = realmId
        val builder = RealmConfiguration.Builder().modules(Realm.getDefaultModule())
        val inMemoryIdentifier = arguments["inMemoryIdentifier"] as String?
        if (inMemoryIdentifier == null) {
        } else {
            builder.inMemory().name(inMemoryIdentifier)
        }
        val config = builder.build()
        Realm.getInstance(config)
        realm = DynamicRealm.getInstance(config)
    }

    constructor(channel: MethodChannel, realmId: String, configuration: RealmConfiguration?) {
        this.channel = channel
        this.realmId = realmId
        Realm.getInstance(configuration)
        realm = DynamicRealm.getInstance(configuration)
    }

    constructor(channel: MethodChannel, realmId: String, realm: Realm) {
        this.channel = channel
        this.realmId = realmId
        this.realm = DynamicRealm.getInstance(realm.configuration)
    }

    fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            val arguments = call.arguments as Map<*, *>
            when (call.method) {
                "createObject" -> {
                    val className = arguments["$"] as String?

                    val primaryKey = if (arguments["uuid"] != null) {
                        arguments["uuid"] as String
                    } else if (arguments["localId"] != null) {
                        arguments["localId"] as String
                    } else if (arguments["id"] != null) {
                        arguments["id"] as String
                    } else {
                        null
                    }

                    realm.beginTransaction()
                    val obj = realm.createObject(className, primaryKey)
                    mapToObject(obj, arguments)
                    realm.commitTransaction()
                    result.success(objectToMap(obj))
                }
                "deleteObject" -> {
                    val className = arguments["$"] as String?
                    val primaryKey = arguments["primaryKey"]
                    val obj = find(className, primaryKey)
                    realm.beginTransaction()
                    obj!!.deleteFromRealm()
                    realm.commitTransaction()
                    result.success(null)
                }
                "allObjects" -> {
                    val className = arguments["$"] as String?
                    val results = realm.where(className).findAll()
                    val list = convert(results)
                    result.success(list)
                }
                "updateObject" -> {
                    val className = arguments["$"] as String?
                    val primaryKey = arguments["primaryKey"]
                    val value = arguments["value"] as HashMap<*, *>?
                    val obj = find(className, primaryKey)
                    if (obj == null) {
                        val msg = String.format("%s not found with primaryKey = %s", className, primaryKey)
                        result.error(msg, null, null)
                        return
                    }
                    realm.beginTransaction()
                    mapToObject(obj, value)
                    realm.commitTransaction()
                    result.success(objectToMap(obj))
                }
                "subscribeAllObjects" -> {
                    val className = arguments["$"] as String?
                    val subscriptionId = arguments["subscriptionId"] as String?
                    val subscription = realm.where(className).findAllAsync()
                    subscribe(subscriptionId, subscription)
                    result.success(null)
                }
                "subscribeObjects" -> {
                    val className = arguments["$"] as String?
                    val subscriptionId = arguments["subscriptionId"] as String?
                    val predicate = arguments["predicate"] as List<List<*>>?
                    val subscription = getQuery(realm.where(className), predicate).findAllAsync()
                    subscribe(subscriptionId, subscription)
                    result.success(null)
                }
                "objects" -> {
                    val className = arguments["$"] as String?
                    val predicate = arguments["predicate"] as List<List<*>>?
                    val results = getQuery(realm.where(className), predicate).findAll()
                    val list = convert(results)
                    result.success(list)
                }
                "unsubscribe" -> {
                    val subscriptionId = arguments["subscriptionId"] as String?
                            ?: throw Exception("No argument: subscriptionId")
                    if (!subscriptions.containsKey(subscriptionId)) {
                        throw Exception("Not subscribed: $subscriptionId")
                    }
                    val subscription = subscriptions.remove(subscriptionId)
                    subscription?.removeAllChangeListeners()
                    result.success(null)
                }
                "deleteAllObjects" -> {
                    realm.beginTransaction()
                    realm.deleteAll()
                    realm.commitTransaction()
                    result.success(null)
                }
                "filePath" -> {
                    result.success(realm.configuration.path)
                }
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            if (realm.isInTransaction) {
                realm.cancelTransaction()
            }
            e.printStackTrace()
            result.error(e.message, e.message, e.stackTrace.toString())
        }
    }

    private fun find(className: String?, primaryKey: Any?): DynamicRealmObject? {
        var obj: DynamicRealmObject? = null
        if (primaryKey is String) {
            obj = realm.where(className).equalTo("uuid", primaryKey as String?).findFirst()
        } else if (primaryKey is Int) {
            obj = realm.where(className).equalTo("uuid", primaryKey as Int?).findFirst()
        }
        return obj
    }

    @Throws(Exception::class)
    private fun getQuery(query: RealmQuery<DynamicRealmObject>, predicate: List<List<*>>?): RealmQuery<DynamicRealmObject> {
        if (predicate == null) {
            return query
        }
        var result = query
        for (item in predicate) {
            val operator = item[0] as String
            result = when (operator) {
                "greaterThan" -> {
                    val fieldName = item[1] as String
                    val argument: Any? = item[2]
                    if (argument is Int) {
                        result.greaterThan(fieldName, argument)
                    } else if (argument is Long) {
                        result.greaterThan(fieldName, argument)
                    } else {
                        throw Exception("Unsupported type")
                    }
                }
                "greaterThanOrEqualTo" -> {
                    val fieldName = item[1] as String
                    val argument: Any? = item[2]
                    if (argument is Int) {
                        result.greaterThanOrEqualTo(fieldName, argument)
                    } else if (argument is Long) {
                        result.greaterThanOrEqualTo(fieldName, argument)
                    } else {
                        throw Exception("Unsupported type")
                    }
                }
                "lessThan" -> {
                    val fieldName = item[1] as String
                    val argument: Any? = item[2]
                    if (argument is Int) {
                        result.lessThan(fieldName, argument)
                    } else if (argument is Long) {
                        result.lessThan(fieldName, argument)
                    } else {
                        throw Exception("Unsupported type")
                    }
                }
                "lessThanOrEqualTo" -> {
                    val fieldName = item[1] as String
                    val argument: Any? = item[2]
                    if (argument is Int) {
                        result.lessThanOrEqualTo(fieldName, argument)
                    } else if (argument is Long) {
                        result.lessThanOrEqualTo(fieldName, argument)
                    } else {
                        throw Exception("Unsupported type")
                    }
                }
                "equalTo" -> {
                    val fieldName = item[1] as String
                    val argument: Any? = item[2]
                    if (argument is Int) {
                        result.equalTo(fieldName, argument)
                    } else if (argument is String) {
                        result.equalTo(fieldName, argument)
                    } else if (argument is Long) {
                        result.equalTo(fieldName, argument)
                    } else {
                        throw Exception("Unsupported type")
                    }
                }
                "notEqualTo" -> {
                    val fieldName = item[1] as String
                    val argument: Any? = item[2]
                    if (argument is Int) {
                        result.notEqualTo(fieldName, argument)
                    } else if (argument is String) {
                        result.notEqualTo(fieldName, argument)
                    } else if (argument is Long) {
                        result.notEqualTo(fieldName, argument)
                    } else {
                        throw Exception("Unsupported type")
                    }
                }
                "contains" -> {
                    val fieldName = item[1] as String
                    val argument: Any? = item[2]
                    if (argument is String) {
                        result.contains(fieldName, argument)
                    } else {
                        throw Exception("Unsupported type")
                    }
                }
                "and" -> result.and()
                "or" -> result.or()
                else -> throw Exception("Unknown operator")
            }
        }
        return result
    }

    @Throws(Exception::class)
    private fun subscribe(subscriptionId: String?, subscription: RealmResults<DynamicRealmObject>) {
        if (subscriptions.containsKey(subscriptionId)) {
            throw Exception("Already subscribed")
        }
        subscriptions[subscriptionId] = subscription
        subscription.addChangeListener { results, changeSet ->
            val list = convert(results)
            val map: MutableMap<String, Any?> = HashMap()
            map["realmId"] = realmId
            map["subscriptionId"] = subscriptionId
            map["results"] = list
            channel.invokeMethod("onResultsChange", Collections.unmodifiableMap(map))
        }
    }

    private fun objectToMap(obj: DynamicRealmObject): HashMap<*, *> {
        val map = HashMap<String, Any>()
        for (fieldName in obj.fieldNames) {
            if (obj.isNull(fieldName)) {
                continue
            }
            if (obj.getFieldType(fieldName) == RealmFieldType.STRING_LIST) {
                val value: Any = obj.getList(fieldName, String::class.java)
                map[fieldName] = value
                continue
            }
            if (obj.getFieldType(fieldName) == RealmFieldType.INTEGER_LIST) {
                val value: Any = obj.getList(fieldName, Int::class.java)
                map[fieldName] = value
                continue
            }
            if (obj.getFieldType(fieldName) === RealmFieldType.OBJECT) {
                val value: DynamicRealmObject = obj.getObject(fieldName) as DynamicRealmObject
                map[fieldName] = objectToMap(value)
                continue
            }
            val value = obj.get<Any>(fieldName)
            map[fieldName] = value.toString()
        }
        return map
    }

    private fun mapToObject(obj: DynamicRealmObject, map: Map<*, *>?) {
        for (fieldName in obj.fieldNames) {
            if (!map!!.containsKey(fieldName) || fieldName == "uuid" || fieldName == "id" || fieldName == "localId") {
                continue
            }

            var value = map[fieldName]

            if (value is List<*>) {
                val newValue: RealmList<*> = RealmList<Any>()
                newValue.addAll(value as Collection<Nothing>)
                value = newValue
            }

            obj[fieldName] = value
        }
    }

    private fun convert(results: RealmResults<DynamicRealmObject>): List<*> {
        val list = ArrayList<Map<*, *>>()
        for (result in results) {
            val map = objectToMap(result)
            list.add(map)
        }
        return Collections.unmodifiableList(list)
    }

    fun reset() {
        subscriptions.clear()
        realm.beginTransaction()
        realm.deleteAll()
        realm.commitTransaction()
    }
}
