package com.it_nomads.flutter_realm

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.realm.ObjectServerError
import io.realm.SyncCredentials
import io.realm.SyncUser
import java.util.*

class SyncUserMethodSubHandler : MethodSubHandler {
    private fun onLogOut(arguments: Map<*, *>, result: MethodChannel.Result) {
        val identity = arguments["identity"] as String?
        val user = SyncUser.all()[identity]
        if (user == null) {
            result.error("User with identity = \"$identity\" not found.", null, null)
        } else {
            user.logOut()
            result.success(null)
        }
    }

    private fun onCurrentUser(result: MethodChannel.Result) {
        val syncUser = SyncUser.current()
        if (syncUser == null) {
            result.success(null)
        } else {
            result.success(Collections.unmodifiableMap<Any, Any>(userToMap(syncUser)))
        }
    }

    private fun onAllUsers(result: MethodChannel.Result) {
        val data = ArrayList<Map<*, *>>()
        for (user in SyncUser.all().values) {
            data.add(userToMap(user))
        }
        result.success(data)
    }

    private fun onLogInWithCredentials(arguments: Map<*, *>, result: MethodChannel.Result) {
        val credentials = credentialsFromArguments(arguments)
        if (credentials == null) {
            val message = "Provider is not supported for authorization. Received: $arguments"
            result.error(message, null, null)
            return
        }
        val url = (arguments["authServerURL"] as String?)!!
        SyncUser.logInAsync(credentials, url, object : SyncUser.Callback<SyncUser> {
            override fun onSuccess(user: SyncUser) {
                val data = userToMap(user)
                result.success(data)
            }

            override fun onError(error: ObjectServerError) {
                result.error(error.errorMessage, null, null)
            }
        })
    }

    private fun userToMap(user: SyncUser): Map<*, *> {
        val data = HashMap<String, Any>()
        data["identity"] = user.identity
        data["isAdmin"] = user.isAdmin
        return data
    }

    private fun credentialsFromArguments(arguments: Map<*, *>): SyncCredentials? {
        val provider = arguments["provider"].toString()
        val data = (arguments["data"] as Map<*, *>?)!!
        if ("jwt" == provider) {
            val jwt = (data["jwt"] as String?)!!
            return SyncCredentials.jwt(jwt)
        }
        if ("username&password" == provider) {
            val username = data["username"] as String?
            val password = data["password"] as String?
            val shouldRegister = data["shouldRegister"] as Boolean?
            return SyncCredentials.usernamePassword(username, password, shouldRegister!!)
        }
        return null
    }

    override fun onMethodCall(call: MethodCall?, result: MethodChannel.Result?): Boolean {
        if (call != null && result != null) {
            return when (call.method) {
                "logInWithCredentials" -> {
                    onLogInWithCredentials(call.arguments as Map<*, *>, result)
                    true
                }
                "allUsers" -> {
                    onAllUsers(result)
                    true
                }
                "currentUser" -> {
                    onCurrentUser(result)
                    true
                }
                "logOut" -> {
                    onLogOut(call.arguments as Map<*, *>, result)
                    true
                }
                else -> false
            }
        }

        return false;
    }
}
