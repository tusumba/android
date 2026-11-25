package com.example.tusumba.notifications

import com.example.tusumba.data.auth.AuthUser
import com.example.tusumba.data.local.TokenManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PushDeviceSynchronizerTest {
    private val api = mockk<PushDeviceApi>()
    private val store = mockk<PushDeviceStore>()
    private val session = mockk<TokenManager>()
    private val environment = mockk<FirebasePushEnvironment>()
    private val firebase = mockk<FirebasePushTokenProvider>()
    private val oldId = UUID.randomUUID()
    private val newId = UUID.randomUUID()
    private val firstUser = AuthUser("first", "first@example.org", "first")
    private var user: AuthUser? = firstUser
    private var binding: PushDeviceBinding? = null
    private val pending = mutableMapOf<String, MutableSet<UUID>>()
    private lateinit var synchronizer: PushDeviceSynchronizer

    @Before
    fun setUp() {
        every { session.getUser() } answers { user }
        every { session.getAccessToken() } returns "access_for_test"
        every { environment.configured() } returns true
        every { environment.supported() } returns true
        every { environment.notificationsEnabled() } returns true
        every { store.retireBinding() } answers {
            binding?.let { pending.getOrPut(it.userId) { mutableSetOf() }.add(it.deviceId) }
            binding = null
        }
        every { store.replaceBinding(any()) } answers {
            val replacement = firstArg<PushDeviceBinding>()
            binding?.let { pending.getOrPut(it.userId) { mutableSetOf() }.add(it.deviceId) }
            binding = replacement
            pending[replacement.userId]?.remove(replacement.deviceId)
        }
        every { store.pendingRemovals(any()) } answers { pending[firstArg()]?.toSet().orEmpty() }
        every { store.forgetRemoval(any(), any()) } answers { pending[firstArg()]?.remove(secondArg()) ?: false; Unit }
        coEvery { firebase.token() } returns "updated_token"
        coEvery { api.register(any()) } returns newId
        coEvery { api.remove(any()) } just Runs
        synchronizer = PushDeviceSynchronizer(api, store, session, environment, firebase)
    }

    @Test
    fun rotatedTokenIsRegisteredAndPreviousServerRecordIsRemoved() = runTest {
        binding = PushDeviceBinding(firstUser.id, "old_token", oldId)

        synchronizer.synchronize()

        assertEquals(PushDeviceBinding(firstUser.id, "updated_token", newId), binding)
        coVerify(exactly = 1) { api.register("updated_token") }
        coVerify(exactly = 1) { api.remove(oldId) }
        assertTrue(pending[firstUser.id].orEmpty().isEmpty())
    }

    @Test
    fun successfulRegistrationRetainsFailedOldTokenCleanupForRetry() = runTest {
        binding = PushDeviceBinding(firstUser.id, "old_token", oldId)
        coEvery { api.remove(oldId) } throws IOException("Offline")

        try { synchronizer.synchronize() } catch (_: IOException) { }

        assertEquals(newId, binding?.deviceId)
        assertEquals(setOf(oldId), pending[firstUser.id])
        coEvery { api.remove(oldId) } just Runs
        synchronizer.synchronize()
        assertTrue(pending[firstUser.id].orEmpty().isEmpty())
        coVerify(exactly = 0) { api.remove(newId) }
    }

    @Test
    fun failedRegistrationKeepsPreviousBindingUntilARepeatSucceeds() = runTest {
        val previous = PushDeviceBinding(firstUser.id, "old_token", oldId)
        binding = previous
        coEvery { api.register(any()) } throws IOException("Offline")

        try { synchronizer.synchronize() } catch (_: IOException) { }

        assertEquals(previous, binding)
        coVerify(exactly = 0) { api.remove(any()) }
        coEvery { api.register(any()) } returns newId
        synchronizer.synchronize()
        assertEquals(newId, binding?.deviceId)
    }

    @Test
    fun guestNeverRegistersADevice() = runTest {
        user = null
        synchronizer.synchronize()
        coVerify(exactly = 0) { firebase.token() }
        coVerify(exactly = 0) { api.register(any()) }
    }

    @Test
    fun deniedPermissionRemovesBindingWithoutRequestingAFirebaseToken() = runTest {
        binding = PushDeviceBinding(firstUser.id, "old_token", oldId)
        every { environment.notificationsEnabled() } returns false

        synchronizer.synchronize()

        assertNull(binding)
        coVerify { api.remove(oldId) }
        coVerify(exactly = 0) { firebase.token() }
        coVerify(exactly = 0) { api.register(any()) }
    }

    @Test
    fun accountChangeDuringTokenLookupDoesNotRegisterForAnotherUser() = runTest {
        val lookup = CompletableDeferred<String>()
        coEvery { firebase.token() } coAnswers { lookup.await() }
        val job = launch { synchronizer.synchronize() }
        runCurrent()
        user = firstUser.copy(id = "second")
        lookup.complete("new_token")
        job.join()
        coVerify(exactly = 0) { api.register(any()) }
        verify(exactly = 0) { store.replaceBinding(any()) }
    }

    @Test
    fun signingOutRetiresTheCurrentBindingAndDeletesItsServerId() = runTest {
        binding = PushDeviceBinding(firstUser.id, "token", oldId)
        synchronizer.unregister()
        assertNull(binding)
        coVerify { api.remove(oldId) }
        assertTrue(pending[firstUser.id].orEmpty().isEmpty())
    }
}
