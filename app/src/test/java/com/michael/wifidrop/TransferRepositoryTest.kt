package com.michael.wifidrop

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.michael.wifidrop.core.common.DispatcherProvider
import com.michael.wifidrop.core.data.TransferRepositoryImpl
import com.michael.wifidrop.core.domain.*
import com.michael.wifidrop.core.network.KtorLocalServer
import com.michael.wifidrop.core.storage.TransferDao
import com.michael.wifidrop.core.storage.TransferSessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransferRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    
    // Use UnconfinedTestDispatcher to run all coroutine operations immediately on the test thread
    private class TestDispatcherProvider : DispatcherProvider {
        private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }
    
    private val dispatchers = TestDispatcherProvider()

    private class FakeTransferDao : TransferDao {
        val sessions = mutableListOf<TransferSessionEntity>()
        
        override fun getAllSessions(): Flow<List<TransferSessionEntity>> {
            return MutableStateFlow(sessions)
        }
        override suspend fun getSessionById(id: String): TransferSessionEntity? {
            return sessions.find { it.id == id }
        }
        override suspend fun insertSession(session: TransferSessionEntity) {
            sessions.add(session)
        }
        override suspend fun deleteSessionById(id: String) {
            sessions.removeAll { it.id == id }
        }
    }

    private class FakeKtorLocalServer(context: Context, dispatchers: DispatcherProvider) :
        KtorLocalServer(context, dispatchers) {
        override suspend fun start(port: Int, shareItems: List<TransferItem>): Int {
            return 12345
        }
        override fun stop() {}
    }

    @Test
    fun testP2PSendProgressAndCompletion() = runTest {
        val fakeDao = FakeTransferDao()
        val fakeKtor = FakeKtorLocalServer(context, dispatchers)
        val repository = TransferRepositoryImpl(context, fakeKtor, fakeDao, dispatchers)

        val items = listOf(
            TransferItem.SingleFile("uri1", "file1.txt", 1000L),
            TransferItem.SingleFile("uri2", "file2.txt", 2000L)
        )
        val target = DiscoveredDevice("device1", "Receiver Device", "192.168.1.100", 33445, 0, true)

        // Start send
        val sessionId = repository.startSend(items, target).getOrNull()
        assertNotNull(sessionId)

        // Initially progress should be 0, state transferring
        val activeSessions1 = repository.observeActiveSessions().first()
        val session1 = activeSessions1.find { it.id == sessionId }
        assertNotNull(session1)
        assertEquals(TransferState.TRANSFERRING, session1!!.state)
        assertEquals(0L, session1.bytesTransferred)
        assertEquals(3000L, session1.bytesTotal)

        // Progress file 1
        fakeKtor.emitDownloadEvent(WebShareDownloadEvent.FileDownloaded("file1.txt", 500L, 1000L))
        val session2 = repository.observeActiveSessions().first().find { it.id == sessionId }!!
        assertEquals(TransferState.TRANSFERRING, session2.state)
        assertEquals(500L, session2.bytesTransferred)

        // Complete file 1 (overall session should NOT be completed, state should still be transferring)
        fakeKtor.emitDownloadEvent(WebShareDownloadEvent.Completed("file1.txt"))
        val session3 = repository.observeActiveSessions().first().find { it.id == sessionId }!!
        assertEquals(TransferState.TRANSFERRING, session3.state)
        assertEquals(1000L, session3.bytesTransferred)

        // Progress file 2
        fakeKtor.emitDownloadEvent(WebShareDownloadEvent.FileDownloaded("file2.txt", 1500L, 2000L))
        val session4 = repository.observeActiveSessions().first().find { it.id == sessionId }!!
        assertEquals(TransferState.TRANSFERRING, session4.state)
        assertEquals(2500L, session4.bytesTransferred) // 1000 (file1) + 1500 (file2)

        // Complete file 2 (all completed)
        fakeKtor.emitDownloadEvent(WebShareDownloadEvent.Completed("file2.txt"))
        val session5 = repository.observeActiveSessions().first().find { it.id == sessionId }!!
        assertEquals(TransferState.COMPLETED, session5.state)
        assertEquals(3000L, session5.bytesTransferred)
    }
}
