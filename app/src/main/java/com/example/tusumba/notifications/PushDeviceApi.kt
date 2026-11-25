package com.example.tusumba.notifications

import com.example.tusumba.data.notifications.api.DefaultApi
import com.example.tusumba.data.notifications.model.DeviceRegistration
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

internal class PushDeviceApiException(val statusCode: Int) : IOException("Device request failed ($statusCode)")

class PushDeviceApi @Inject constructor(private val api: DefaultApi) {
    suspend fun register(token: String): UUID {
        val response = api.registerDevice(DeviceRegistration(DeviceRegistration.Platform.android, token))
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw PushDeviceApiException(response.code())
        }
        return response.body()?.id ?: throw IOException("Empty device registration response")
    }

    suspend fun remove(deviceId: UUID) {
        val response = api.deleteDevice(deviceId)
        response.errorBody()?.close()
        if (!response.isSuccessful && response.code() != 404) throw PushDeviceApiException(response.code())
    }
}
